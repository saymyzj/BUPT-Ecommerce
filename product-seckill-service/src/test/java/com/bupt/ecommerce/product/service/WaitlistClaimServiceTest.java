package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.product.entity.SeckillWaitlistEntry;
import com.bupt.ecommerce.product.entity.WaitlistStatus;
import com.bupt.ecommerce.product.repository.SeckillWaitlistRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaitlistClaimServiceTest {

    @Test
    void shouldClaimServerOrderedFirstWaiter() {
        SeckillWaitlistRepository repository = mock(SeckillWaitlistRepository.class);
        WaitlistClaimService service = new WaitlistClaimService(repository);
        SeckillWaitlistEntry first = entry(1L, 10001L);
        SeckillWaitlistEntry second = entry(2L, 10002L);
        when(repository.findForPromotion(1L, WaitlistStatus.WAITING)).thenReturn(List.of(first, second));
        when(repository.save(any(SeckillWaitlistEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SeckillWaitlistEntry claimed = service.claimNext(1L);

        assertEquals(1L, claimed.getId());
        assertEquals(WaitlistStatus.PROMOTING, claimed.getStatus());
        assertEquals(WaitlistStatus.WAITING, second.getStatus());
    }

    private SeckillWaitlistEntry entry(Long id, Long userId) {
        SeckillWaitlistEntry entry = new SeckillWaitlistEntry();
        entry.setId(id);
        entry.setActivityId(1L);
        entry.setUserId(userId);
        entry.setQuantity(1);
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        return entry;
    }
}
