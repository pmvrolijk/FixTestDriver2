package nl.lamia.fixtestdriver.repository;

import nl.lamia.fixtestdriver.domain.LatencyTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LatencyTraceRepository extends JpaRepository<LatencyTraceEntity, Long> {
    List<LatencyTraceEntity> findByTestResultId(Long testResultId);
}
