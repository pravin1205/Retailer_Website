package com.marketly.customer.service;

import com.marketly.common.dto.PageResponse;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.customer.dto.AddressRequest;
import com.marketly.customer.dto.CustomerRequest;
import com.marketly.customer.entity.Address;
import com.marketly.customer.entity.Customer;
import com.marketly.customer.repository.AddressRepository;
import com.marketly.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository  addressRepository;

    // ── Profile ────────────────────────────────────────────────────────────

    /**
     * Called by the Kafka consumer when identity.user.registered fires.
     * Creates a Customer record for this tenant if one doesn't exist yet.
     */
    @Transactional
    public Customer getOrCreate(UUID tenantId, UUID userId, String email,
                                String firstName, String lastName, String phone) {
        return customerRepository
            .findByTenantIdAndUserIdAndDeletedAtIsNull(tenantId, userId)
            .orElseGet(() -> {
                Customer c = Customer.builder()
                    .userId(userId)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .phone(phone)
                    .build();
                c.setTenantId(tenantId);
                Customer saved = customerRepository.save(c);
                log.info("Customer profile created: userId={} tenantId={}", userId, tenantId);
                return saved;
            });
    }

    @Transactional(readOnly = true)
    public Customer getByUserAndTenant(UUID userId, UUID tenantId) {
        return customerRepository
            .findByTenantIdAndUserIdAndDeletedAtIsNull(tenantId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Customer profile not found for this store."));
    }

    @Transactional
    public Customer updateProfile(UUID userId, UUID tenantId, CustomerRequest req) {
        Customer customer = getByUserAndTenant(userId, tenantId);
        if (req.getFirstName()   != null) customer.setFirstName(req.getFirstName());
        if (req.getLastName()    != null) customer.setLastName(req.getLastName());
        if (req.getPhone()       != null) customer.setPhone(req.getPhone());
        if (req.getEmail()       != null) customer.setEmail(req.getEmail());
        if (req.getDateOfBirth() != null) customer.setDateOfBirth(req.getDateOfBirth());
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<Customer> listCustomers(UUID tenantId, String search,
                                                int page, int size) {
        PageRequest pageable = PageRequest.of(
            page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> result = customerRepository
            .findByTenantIdFiltered(tenantId, search, pageable);
        return PageResponse.of(result);
    }

    // ── Loyalty ────────────────────────────────────────────────────────────

    /**
     * Called by Kafka consumer after PaymentCompleted.
     * Awards 1 point per 10 rupees spent.
     */
    @Transactional
    public void awardLoyaltyPoints(UUID tenantId, UUID userId, BigDecimal orderTotal) {
        customerRepository
            .findByTenantIdAndUserIdAndDeletedAtIsNull(tenantId, userId)
            .ifPresent(customer -> {
                int points = orderTotal.intValue() / 10;
                if (points > 0) {
                    customer.addLoyaltyPoints(points);
                    customer.recordOrder(orderTotal);
                    customerRepository.save(customer);
                    log.info("Awarded {} loyalty points to customer {} (tenant {})",
                             points, userId, tenantId);
                }
            });
    }

    // ── Addresses ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Address> listAddresses(UUID userId, UUID tenantId) {
        Customer customer = getByUserAndTenant(userId, tenantId);
        return addressRepository.findByCustomerIdAndDeletedAtIsNull(customer.getId());
    }

    @Transactional
    public Address addAddress(UUID userId, UUID tenantId, AddressRequest req) {
        Customer customer = getByUserAndTenant(userId, tenantId);

        if (req.isDefault()) {
            addressRepository.clearDefaultForCustomer(customer.getId());
        }

        Address address = Address.builder()
            .customer(customer)
            .label(req.getLabel())
            .line1(req.getLine1())
            .line2(req.getLine2())
            .city(req.getCity())
            .state(req.getState())
            .pincode(req.getPincode())
            .isDefault(req.isDefault())
            .build();
        address.setTenantId(tenantId);
        return addressRepository.save(address);
    }

    @Transactional
    public Address updateAddress(UUID userId, UUID tenantId,
                                 UUID addressId, AddressRequest req) {
        Customer customer = getByUserAndTenant(userId, tenantId);
        Address address = addressRepository
            .findByIdAndCustomerIdAndDeletedAtIsNull(addressId, customer.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId.toString()));

        if (req.isDefault()) {
            addressRepository.clearDefaultForCustomer(customer.getId());
        }

        if (req.getLine1()   != null) address.setLine1(req.getLine1());
        if (req.getLine2()   != null) address.setLine2(req.getLine2());
        if (req.getCity()    != null) address.setCity(req.getCity());
        if (req.getState()   != null) address.setState(req.getState());
        if (req.getPincode() != null) address.setPincode(req.getPincode());
        if (req.getLabel()   != null) address.setLabel(req.getLabel());
        address.setDefault(req.isDefault());

        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID tenantId, UUID addressId) {
        Customer customer = getByUserAndTenant(userId, tenantId);
        Address address = addressRepository
            .findByIdAndCustomerIdAndDeletedAtIsNull(addressId, customer.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId.toString()));
        address.softDelete();
        addressRepository.save(address);
    }
}
