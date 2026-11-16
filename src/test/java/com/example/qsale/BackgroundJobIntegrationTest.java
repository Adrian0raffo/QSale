package com.example.qsale;

import com.example.qsale.jobs.*;
import com.example.qsale.notification.domain.EmailService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="jobs.enabled=false")
class BackgroundJobIntegrationTest extends AbstractContainerBaseTest {
    @Autowired BackgroundQueue queue;
    @Autowired BackgroundJobExecutor executor;
    @Autowired BackgroundJobProcessor processor;
    @Autowired BackgroundJobRepository jobs;
    @Autowired PlatformTransactionManager transactions;
    @Value("${jobs.scope}") String scope;
    @MockitoBean EmailService email;

    @AfterEach void clean() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> jobs.findAll().stream()
                .filter(job -> job.getScope().equals(scope)).forEach(jobs::delete));
    }

    @Test void aFailedDeliveryKeepsItsDurableJobAndRetriesOnlyWhenDue() {
        String id=enqueue();
        when(email.sendHtmlEmail(anyString(),anyString(),anyString(),anyMap())).thenReturn(false);
        assertEquals(id,queue.claim());
        RuntimeException failure=assertThrows(RuntimeException.class,()->executor.execute(id));
        queue.failed(id,failure);
        BackgroundJob pending=jobs.findById(id).orElseThrow();
        assertEquals("PENDING",pending.getStatus());assertEquals(1,pending.getAttempts());
        assertTrue(pending.getAvailableAt().isAfter(Instant.now()));assertNull(queue.claim());
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                jobs.findById(id).orElseThrow().setAvailableAt(Instant.now().minusSeconds(1)));
        when(email.sendHtmlEmail(anyString(),anyString(),anyString(),anyMap())).thenReturn(true);
        assertEquals(id,queue.claim());executor.execute(id);
        assertEquals("DONE",jobs.findById(id).orElseThrow().getStatus());
        verify(email,times(2)).sendHtmlEmail(anyString(),anyString(),anyString(),anyMap());
    }

    @Test void anAbandonedLeaseCanBeRecoveredAfterAWorkerRestart() {
        String id=enqueue();assertEquals(id,queue.claim());
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                jobs.findById(id).orElseThrow().setLeaseUntil(Instant.now().minusSeconds(1)));
        assertEquals(id,queue.claim());
        when(email.sendHtmlEmail(anyString(),anyString(),anyString(),anyMap())).thenReturn(true);
        executor.execute(id);
        assertEquals(2,jobs.findById(id).orElseThrow().getAttempts());assertNull(queue.claim());
    }

    @Test void aRolledBackOperationDoesNotLeaveAJobToDeliver() {
        String id=new TransactionTemplate(transactions).execute(status -> {
            String created=queue.enqueue("EMAIL",mail());status.setRollbackOnly();return created;
        });
        assertFalse(jobs.existsById(id));assertNull(queue.claim());
    }

    @Test void aBatchUsesTheAsyncExecutorAndCompletesOnlyAfterItsJobsAreSaved() throws Exception {
        String id = enqueue();
        var deliveryThread = new java.util.concurrent.atomic.AtomicReference<String>();
        when(email.sendHtmlEmail(anyString(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
            deliveryThread.set(Thread.currentThread().getName());
            return true;
        });

        processor.processBatch().get(10, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(deliveryThread.get().startsWith("qsale-async-"));
        assertEquals("DONE", jobs.findById(id).orElseThrow().getStatus());
        assertNull(queue.claim());
    }

    private String enqueue() {
        return new TransactionTemplate(transactions).execute(status -> queue.enqueue("EMAIL",mail()));
    }
    private JobPayload mail() {
        return new JobPayload(null,null,"recipient@qsale.test","Test delivery","email/welcome",Map.of("name","Test"));
    }
}
