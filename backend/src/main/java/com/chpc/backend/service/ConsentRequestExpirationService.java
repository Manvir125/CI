package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.repository.ConsentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentRequestExpirationService {

    private final ConsentRequestRepository consentRequestRepository;

    @Value("${app.token-expiry-hours:72}")
    private int tokenExpiryHours;

    
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireOldConsentRequests() {
        log.info("Running scheduled task to expire old consent requests...");

        LocalDateTime expirationTime = LocalDateTime.now().minusHours(tokenExpiryHours);

        List<ConsentRequest> expiredRequests = consentRequestRepository
                .findByStatusInAndCreatedAtBefore(List.of("PENDING", "SENT"), expirationTime);

        if (expiredRequests.isEmpty()) {
            log.info("No consent requests found to expire.");
            return;
        }

        log.info("Found {} consent requests exceeding {} hours. Updating status to EXPIRED.",
                expiredRequests.size(), tokenExpiryHours);

        for (ConsentRequest request : expiredRequests) {
            request.setStatus("EXPIRED");
            consentRequestRepository.save(request);

            log.info("Consent request ID {} marked as EXPIRED.", request.getId());

        
        }

        log.info("Finished expiring old consent requests.");
    }
}
