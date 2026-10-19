package com.mindex.challenge.exception;

import com.mindex.challenge.data.Compensation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CompensationConflictException extends RuntimeException {
    public CompensationConflictException(Compensation existing) {
        super(String.format("Employee %s already has a compensation effective %s (compensationId: %s)",
                existing.getEmployeeId(), existing.getEffectiveDate(), existing.getCompensationId()));
    }
}
