package com.temadison.stockdash.backend.service;

import com.temadison.stockdash.backend.domain.JobRunEntity;
import com.temadison.stockdash.backend.domain.JobRunStatus;
import com.temadison.stockdash.backend.repository.JobRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class JobRunRecorder {

    private static final int MAX_DETAILS_LENGTH = 1024;

    private final JobRunRepository jobRunRepository;

    public JobRunRecorder(JobRunRepository jobRunRepository) {
        this.jobRunRepository = jobRunRepository;
    }

    @Transactional
    public long start(String jobName, String details) {
        JobRunEntity run = new JobRunEntity();
        run.setJobName(jobName);
        run.setStartedAt(LocalDateTime.now());
        run.setStatus(JobRunStatus.RUNNING);
        run.setRequestedCount(0);
        run.setProcessedCount(0);
        run.setFailedCount(0);
        run.setSkippedCount(0);
        run.setDetails(trim(details));
        return jobRunRepository.save(run).getId();
    }

    @Transactional
    public void success(long runId, int requested, int processed, int failed, int skipped, String details) {
        JobRunEntity run = jobRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job run id: " + runId));
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus(JobRunStatus.SUCCESS);
        run.setRequestedCount(requested);
        run.setProcessedCount(processed);
        run.setFailedCount(failed);
        run.setSkippedCount(skipped);
        run.setDetails(trim(details));
        jobRunRepository.save(run);
    }

    @Transactional
    public void fail(long runId, int requested, int processed, int failed, int skipped, Throwable error, String details) {
        JobRunEntity run = jobRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job run id: " + runId));
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus(JobRunStatus.FAILED);
        run.setRequestedCount(requested);
        run.setProcessedCount(processed);
        run.setFailedCount(failed);
        run.setSkippedCount(skipped);
        String errorText = error == null ? "" : (error.getClass().getSimpleName() + ": " + error.getMessage());
        String combined = details == null || details.isBlank() ? errorText : details + " | " + errorText;
        run.setDetails(trim(combined));
        jobRunRepository.save(run);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_DETAILS_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_DETAILS_LENGTH);
    }
}
