package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.CompensationRepository;
import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.exception.CompensationConflictException;
import com.mindex.challenge.service.CompensationService;
import com.mindex.challenge.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationServiceImpl implements CompensationService {
    private final CompensationRepository compensationRepository;
    private final EmployeeService employeeService;

    @Override
    public Compensation create(String employeeId, Compensation compensation) {
        log.debug("Creating compensation for employee [{}] effective [{}]", employeeId, compensation.getEffectiveDate());

        employeeService.read(employeeId); // 404 if the employee doesn't exist

        compensationRepository.findByEmployeeIdAndEffectiveDate(employeeId, compensation.getEffectiveDate())
                .ifPresent(existing -> {
                    throw new CompensationConflictException(existing);
                });

        // Ids are always assigned server-side, matching EmployeeServiceImpl.create; any client-sent values are ignored
        compensation.setCompensationId(UUID.randomUUID().toString());
        compensation.setEmployeeId(employeeId);

        Compensation created = compensationRepository.insert(compensation);
        log.info("Created compensation [{}] for employee [{}] effective [{}]",
                created.getCompensationId(), created.getEmployeeId(), created.getEffectiveDate());

        return created;
    }

    @Override
    public List<Compensation> read(String employeeId) {
        log.debug("Reading compensation history for employee [{}]", employeeId);

        employeeService.read(employeeId);
        return compensationRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId);
    }
}
