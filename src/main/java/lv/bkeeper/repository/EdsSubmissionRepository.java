package lv.bkeeper.repository;

import lv.bkeeper.domain.EdsSubmission;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface EdsSubmissionRepository extends CrudRepository<EdsSubmission, Long> {

    Optional<EdsSubmission> findByDocumentTypeAndPeriodYearAndPeriodMonth(
            String documentType, int year, int month);

    List<EdsSubmission> findByPeriodYearOrderByPeriodMonth(int year);

    List<EdsSubmission> findByStatus(String status);
}
