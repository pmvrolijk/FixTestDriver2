package nl.lamia.fixtestdriver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.domain.MessageEntity;
import nl.lamia.fixtestdriver.domain.OrderEntity;
import nl.lamia.fixtestdriver.repository.MessageRepository;
import nl.lamia.fixtestdriver.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quickfix.FieldNotFound;
import quickfix.Message;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderManagerService {

    private final OrderRepository orderRepository;
    private final MessageRepository messageRepository;

    // Cache to match templates to actual order IDs, similar to the legacy orderIds HashMap
    private final Map<String, String> orderIdMapping = new ConcurrentHashMap<>();

    @Transactional
    public void addMessage(Message message) {
        String clordid = getSafeField(message, 11);
        String origClordid = getSafeField(message, 41);
        String msgType = getSafeHeaderField(message, 35);
        String orderStatus = getSafeField(message, 39);

        if (!clordid.isEmpty()) {
            messageRepository.save(MessageEntity.builder()
                    .clordid(clordid)
                    .insertTime(LocalDateTime.now())
                    .fixMessage(message.toString().replace('\001', '|'))
                    .build());
        }

        log.info("Order: {} origClordid: {} msgtype: {} status: {}.", clordid, origClordid, msgType, orderStatus);

        updateOrderStatus(clordid, origClordid, msgType, orderStatus);
    }

    private void updateOrderStatus(String clordid, String origClordid, String msgType, String orderStatus) {
        String updStatus = null;
        String origUpdStatus = null;

        // Outgoing messages
        switch (msgType) {
            case "D" -> updStatus = "NewSingle";
            case "AB" -> updStatus = "NewMulti";
            case "G" -> {
                updStatus = "Replace";
                origUpdStatus = "ReplaceRequest";
            }
            case "F" -> {
                updStatus = "Cancel";
                origUpdStatus = "CancelRequest";
            }
        }

        if (updStatus != null) {
            updateStatus(clordid, updStatus);
        }
        if (origUpdStatus != null) {
            updateStatus(origClordid, origUpdStatus);
        }

        // Incoming messages (Execution Report '8' or Order Cancel Reject '9')
        if ("8".equals(msgType) && !orderStatus.isEmpty()) {
            updateStatus(clordid, orderStatus);
        } else if ("9".equals(msgType)) {
            updateStatus(clordid, "CancRej");
            if (!origClordid.isEmpty()) {
                updateStatus(origClordid, "CancRej");
            }
        }
    }

    private void updateStatus(String clordid, String status) {
        if (clordid == null || clordid.isEmpty()) return;
        orderRepository.findById(clordid).ifPresent(order -> {
            order.setStatus(status);
            orderRepository.save(order);
        });
    }

    @Transactional
    public void addOrderId(String templateId, String actualId) {
        orderIdMapping.put(templateId, actualId);
        orderRepository.save(OrderEntity.builder()
                .clordid(actualId)
                .testid(templateId)
                .insertTime(LocalDateTime.now())
                .status("INIT")
                .build());
    }

    public String getActualOrderId(String templateId) {
        return orderIdMapping.get(templateId);
    }

    @Transactional
    public void purgeAll() {
        messageRepository.deleteAll();
        orderRepository.deleteAll();
        orderIdMapping.clear();
    }

    @Transactional
    public void purgeTestCase(String testid) {
        // Find orders for this test case to delete associated messages
        orderRepository.findByTestid(testid).forEach(order -> {
            messageRepository.deleteByClordid(order.getClordid());
            orderRepository.delete(order);
        });
        // We might also want to clear from orderIdMapping if applicable
        orderIdMapping.remove(testid);
    }

    private String getSafeField(Message message, int tag) {
        try {
            return message.getString(tag);
        } catch (FieldNotFound e) {
            return "";
        }
    }

    private String getSafeHeaderField(Message message, int tag) {
        try {
            return message.getHeader().getString(tag);
        } catch (FieldNotFound e) {
            return "";
        }
    }
}
