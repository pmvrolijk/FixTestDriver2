package nl.lamia.fixtestdriver.repository;

import nl.lamia.fixtestdriver.domain.LatencyTraceEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LatencyTraceEventRepository extends JpaRepository<LatencyTraceEventEntity, Long> {
    List<LatencyTraceEventEntity> findByTraceIdIn(List<Long> traceIds);
}
