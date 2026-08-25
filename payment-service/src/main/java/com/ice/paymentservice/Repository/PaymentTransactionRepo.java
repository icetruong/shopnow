package com.ice.paymentservice.Repository;

import com.ice.paymentservice.Entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentTransactionRepo extends JpaRepository<PaymentTransaction, UUID> {
}
