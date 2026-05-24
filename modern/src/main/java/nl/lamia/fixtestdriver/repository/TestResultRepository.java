package nl.lamia.fixtestdriver.repository;

import nl.lamia.fixtestdriver.domain.TestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestResultRepository extends JpaRepository<TestResultEntity, Long> {
}
