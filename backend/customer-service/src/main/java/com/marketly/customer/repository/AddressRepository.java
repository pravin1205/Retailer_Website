package com.marketly.customer.repository;

import com.marketly.customer.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByCustomerIdAndDeletedAtIsNull(UUID customerId);

    Optional<Address> findByIdAndCustomerIdAndDeletedAtIsNull(UUID id, UUID customerId);

    // Unset all defaults for a customer before setting a new one
    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false " +
           "WHERE a.customer.id = :customerId AND a.deletedAt IS NULL")
    void clearDefaultForCustomer(@Param("customerId") UUID customerId);
}
