package com.tech.docflow.repository;

import com.tech.docflow.models.Notification;
import com.tech.docflow.models.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdAndStatus(Long recipientId, NotificationStatus status);
    List<Notification> findByRecipientId(Long recipientId);
}