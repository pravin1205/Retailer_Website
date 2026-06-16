package com.marketly.order.service;

import com.marketly.common.dto.PageResponse;
import com.marketly.common.exception.BusinessException;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.order.entity.Order;
import com.marketly.order.event.OrderEventProducer;
import com.marketly.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final Map<String, String[]> VALID_TRANSITIONS = Map.of(
        "PLACED",            new String[]{"CONFIRMED", "CANCELLED"},
        "CONFIRMED",         new String[]{"PACKING",   "CANCELLED"},
        "PACKING",           new String[]{"OUT_FOR_DELIVERY", "CANCELLED"},
        "OUT_FOR_DELIVERY",  new String[]{"DELIVERED"},
        "DELIVERED",         new String[]{"RETURN_REQUESTED"},
        "RETURN_REQUESTED",  new String[]{"RETURNED"}
    );

    private final OrderRepository    orderRepository;
    private final OrderEventProducer orderEventProducer;

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId, UUID tenantId) {
        return orderRepository.findByIdAndTenantId(orderId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId.toString()));
    }

    @Transactional(readOnly = true)
    public PageResponse<Order> listOrdersForCustomer(UUID tenantId, UUID customerId,
                                                     int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size);
        Page<Order> result = orderRepository
            .findByTenantIdAndCustomerIdOrderByPlacedAtDesc(tenantId, customerId, pageable);
        return PageResponse.of(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<Order> listOrdersForTenant(UUID tenantId, String status,
                                                   String search, int page, int size) {
        PageRequest pageable = PageRequest.of(
            page - 1, size, Sort.by(Sort.Direction.DESC, "placedAt"));
        Page<Order> result = orderRepository
            .findByTenantFiltered(tenantId, status, search, pageable);
        return PageResponse.of(result);
    }

    @Transactional
    public Order updateStatus(UUID orderId, UUID tenantId,
                              String newStatus, String note) {
        Order order = getOrder(orderId, tenantId);
        validateTransition(order.getStatus(), newStatus);

        String previousStatus = order.getStatus();
        order.setStatus(newStatus);

        if ("CONFIRMED".equals(newStatus))  order.setConfirmedAt(Instant.now());
        if ("DELIVERED".equals(newStatus))  order.setDeliveredAt(Instant.now());
        if ("CANCELLED".equals(newStatus))  order.setCancelledAt(Instant.now());

        Order saved = orderRepository.save(order);
        orderEventProducer.publishStatusChanged(saved, previousStatus, note);
        return saved;
    }

    @Transactional
    public Order cancelOrder(UUID orderId, UUID tenantId,
                             UUID customerId, String reason) {
        Order order = getOrder(orderId, tenantId);

        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessException("FORBIDDEN",
                "You can only cancel your own orders.", HttpStatus.FORBIDDEN);
        }

        validateTransition(order.getStatus(), "CANCELLED");

        order.setStatus("CANCELLED");
        order.setCancelledAt(Instant.now());
        order.setCancellationReason(reason);

        Order saved = orderRepository.save(order);
        orderEventProducer.publishOrderCancelled(saved);
        return saved;
    }

    private void validateTransition(String current, String next) {
        String[] allowed = VALID_TRANSITIONS.get(current);
        if (allowed == null) {
            throw new BusinessException("INVALID_STATUS_TRANSITION",
                "Order in status '" + current + "' cannot be transitioned.",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }
        for (String a : allowed) {
            if (a.equals(next)) return;
        }
        throw new BusinessException("INVALID_STATUS_TRANSITION",
            "Cannot transition order from '" + current + "' to '" + next + "'.",
            HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
