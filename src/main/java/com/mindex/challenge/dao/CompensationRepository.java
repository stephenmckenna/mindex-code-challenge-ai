package com.mindex.challenge.dao;

import com.mindex.challenge.data.Compensation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompensationRepository extends MongoRepository<Compensation, String> {
    List<Compensation> findByEmployeeIdOrderByEffectiveDateDesc(String employeeId);
    Optional<Compensation> findByEmployeeIdAndEffectiveDate(String employeeId, LocalDate effectiveDate);
}
