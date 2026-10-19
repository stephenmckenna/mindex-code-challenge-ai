package com.mindex.challenge.controller;

import com.mindex.challenge.data.ReportingStructure;
import com.mindex.challenge.service.ReportingStructureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ReportingStructureController {

    private final ReportingStructureService reportingStructureService;

    @GetMapping("/employee/{id}/reportingStructure")
    public ReportingStructure read(@PathVariable String id) {
        log.debug("Received reporting structure request for id [{}]", id);
        return reportingStructureService.read(id);
    }
}