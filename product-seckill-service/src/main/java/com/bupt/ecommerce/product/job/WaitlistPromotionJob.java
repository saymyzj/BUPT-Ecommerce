package com.bupt.ecommerce.product.job;

import com.bupt.ecommerce.product.entity.WaitlistStatus;
import com.bupt.ecommerce.product.repository.SeckillWaitlistRepository;
import com.bupt.ecommerce.product.service.TaskLeaseService;
import com.bupt.ecommerce.product.service.WaitlistPromotionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "app.seckill-waitlist", name = "enabled", havingValue = "true")
public class WaitlistPromotionJob {

    private final SeckillWaitlistRepository repository;
    private final WaitlistPromotionService promotionService;
    private final TaskLeaseService taskLeaseService;

    public WaitlistPromotionJob(
            SeckillWaitlistRepository repository,
            WaitlistPromotionService promotionService,
            TaskLeaseService taskLeaseService
    ) {
        this.repository = repository;
        this.promotionService = promotionService;
        this.taskLeaseService = taskLeaseService;
    }

    @Scheduled(fixedDelayString = "${app.seckill-waitlist.promotion-delay-ms:2000}")
    public void promoteAvailableWaiters() {
        String taskName = "seckill-waitlist-promotion";
        if (!taskLeaseService.tryAcquire(taskName, Duration.ofMinutes(1))) {
            return;
        }
        try {
            for (Long activityId : repository.findActivityIdsByStatus(WaitlistStatus.WAITING)) {
                promotionService.promoteOne(activityId);
            }
        } finally {
            taskLeaseService.release(taskName);
        }
    }
}
