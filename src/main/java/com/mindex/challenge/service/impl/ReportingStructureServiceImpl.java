package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.EmployeeRepository;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.data.ReportingStructure;
import com.mindex.challenge.service.EmployeeService;
import com.mindex.challenge.service.ReportingStructureService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportingStructureServiceImpl implements ReportingStructureService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportingStructureServiceImpl.class);

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;

    @Override
    public ReportingStructure read(String employeeId) {
        LOG.debug("Computing reporting structure for employee id [{}]", employeeId);

        Employee employee = employeeService.read(employeeId);

        Set<String> visited = new HashSet<>();
        visited.add(employeeId);
        int numberOfReports = 0;

        List<String> frontier = directReportIds(employee);
        while (!frontier.isEmpty()) {
            List<String> toFetch = new ArrayList<>();
            for (String id : frontier) {
                if (visited.add(id)) {
                    toFetch.add(id);
                } else {
                    LOG.warn("Employee [{}] reached more than once under [{}] (duplicate or cycle); counting once", id, employeeId);
                }
            }
            if (toFetch.isEmpty()) {
                break;
            }

            List<Employee> reports = employeeRepository.findByEmployeeIdIn(toFetch);
            if (reports.size() < toFetch.size()) {
                Set<String> found = reports.stream().map(Employee::getEmployeeId).collect(Collectors.toSet());
                toFetch.stream()
                        .filter(id -> !found.contains(id))
                        .forEach(id -> LOG.warn("Direct report [{}] under [{}] does not exist; skipping", id, employeeId));
            }

            numberOfReports += reports.size();
            frontier = reports.stream()
                    .flatMap(report -> directReportIds(report).stream())
                    .toList();
        }

        return new ReportingStructure(employee, numberOfReports);
    }

    private static List<String> directReportIds(Employee employee) {
        if (employee.getDirectReports() == null) {
            return List.of();
        }
        return employee.getDirectReports().stream()
                .map(Employee::getEmployeeId)
                .filter(Objects::nonNull)
                .toList();
    }
}