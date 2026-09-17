package com.sms.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_contacts", indexes = @Index(name = "idx_contact_owner", columnList = "owner_id"))
public class Contact {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 15)
    private String phoneNumber;
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public void setName(String name) { this.name = name; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
