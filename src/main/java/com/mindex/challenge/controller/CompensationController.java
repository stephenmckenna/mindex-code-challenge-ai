package com.mindex.challenge.controller;

import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.service.CompensationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CompensationController {

    private final CompensationService compensationService;

    @PostMapping("/employee/{id}/compensation")
    public Compensation create(@PathVariable String id, @Valid @RequestBody Compensation compensation) {
        log.debug("Received compensation create request for id [{}] and compensation [{}]", id, compensation);

        return compensationService.create(id, compensation);
    }

    @GetMapping("/employee/{id}/compensation")
    public List<Compensation> read(@PathVariable String id) {
        log.debug("Received compensation read request for id [{}]", id);

        return compensationService.read(id);
    }
}
