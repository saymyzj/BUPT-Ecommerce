package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.product.entity.SeckillWaitlistEntry;
import com.bupt.ecommerce.product.entity.WaitlistStatus;
import com.bupt.ecommerce.product.repository.SeckillWaitlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WaitlistClaimService {

    private final SeckillWaitlistRepository repository;

    public WaitlistClaimService(SeckillWaitlistRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SeckillWaitlistEntry claimNext(Long activityId) {
        return repository.findForPromotion(activityId, WaitlistStatus.WAITING).stream()
                .findFirst()
                .map(entry -> {
                    entry.setStatus(WaitlistStatus.PROMOTING);
                    entry.setUpdatedAt(LocalDateTime.now());
                    return repository.save(entry);
                })
                .orElse(null);
    }

    @Transactional
    public void updateStatus(Long id, WaitlistStatus status) {
        repository.findById(id).ifPresent(entry -> {
            entry.setStatus(status);
            entry.setUpdatedAt(LocalDateTime.now());
            repository.save(entry);
        });
    }
}
