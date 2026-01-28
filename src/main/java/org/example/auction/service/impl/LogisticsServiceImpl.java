package org.example.auction.service.impl;

import org.example.auction.entity.Logistics;
import org.example.auction.mapper.LogisticsMapper;
import org.example.auction.service.LogisticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LogisticsServiceImpl implements LogisticsService {

    private final LogisticsMapper logisticsMapper;

    public LogisticsServiceImpl(LogisticsMapper logisticsMapper) {
        this.logisticsMapper = logisticsMapper;
    }

    @Override
    public Logistics getByOrderId(Long orderId) {
        return logisticsMapper.findByOrderId(orderId);
    }

    @Override
    @Transactional
    public Logistics saveOrUpdate(Long orderId, String company, String trackingNo, String notes) {
        Logistics existing = logisticsMapper.findByOrderId(orderId);
        if (existing == null) {
            existing = Logistics.builder()
                    .orderId(orderId)
                    .company(company)
                    .trackingNo(trackingNo)
                    .notes(notes)
                    .updatedAt(LocalDateTime.now())
                    .build();
            logisticsMapper.insert(existing);
        } else {
            existing.setCompany(company);
            existing.setTrackingNo(trackingNo);
            existing.setNotes(notes);
            existing.setUpdatedAt(LocalDateTime.now());
            logisticsMapper.updateById(existing);
        }
        return existing;
    }
}
