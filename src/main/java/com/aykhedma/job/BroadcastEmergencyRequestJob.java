package com.aykhedma.job;

import com.aykhedma.service.EmergencyRequestService;
import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class BroadcastEmergencyRequestJob implements Job
{
    private final EmergencyRequestService emergencyRequestService;

    @Override
    public void execute(JobExecutionContext context)
    {
        Long requestId = context.getJobDetail().getJobDataMap().getLong("requestId");
        emergencyRequestService.broadcastEmergencyRequest(requestId);
    }
}
