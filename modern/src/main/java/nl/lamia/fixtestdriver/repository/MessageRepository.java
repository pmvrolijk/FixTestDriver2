package nl.lamia.fixtestdriver.repository;

import nl.lamia.fixtestdriver.domain.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByClordid(String clordid);
    void deleteByClordid(String clordid);
}
