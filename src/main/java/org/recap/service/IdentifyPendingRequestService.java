package org.recap.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.recap.PropertyKeyConstants;
import org.recap.ScsbConstants;
import org.recap.camel.EmailPayLoad;
import org.recap.model.jpa.PendingRequestEntity;
import org.recap.model.jpa.RequestItemEntity;
import org.recap.repository.jpa.PendingRequestDetailsRespository;
import org.recap.repository.jpa.RequestItemDetailsRepository;
import org.recap.repository.jpa.RequestItemStatusDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class IdentifyPendingRequestService {


    @Autowired
    RequestItemDetailsRepository requestItemDetailsRepository;

    @Autowired
    RequestItemStatusDetailsRepository requestItemStatusDetailsRepository;

    @Autowired
    PendingRequestDetailsRespository pendingRequestDetailsRespository;

    @Autowired
    ProducerTemplate producerTemplate;

    @Value("${" + PropertyKeyConstants.EMAIL_PENDING_REQUEST_TO + "}")
    private String pendingRequestEmailTo;

    @Value("${" + PropertyKeyConstants.EMAIL_PENDING_REQUEST_CC + "}")
    private String pendingRequestEmailCc;

    public boolean identifyPendingRequest(){
        List<RequestItemEntity> requestItemEntitiesInPendingList = new ArrayList<>();
        List<RequestItemEntity> requestItemEntitiesInLASList = new ArrayList<>();
        List<PendingRequestEntity> pendingRequestEntityList = new ArrayList<>();
        List<RequestItemEntity> requestItemEntitiesInPendingLASList = requestItemDetailsRepository.findPendingAndLASReqNotNotified(Arrays.asList(ScsbConstants.REQUEST_STATUS_PENDING, ScsbConstants.REQUEST_STATUS_LAS_ITEM_STATUS_PENDING));
        requestItemEntitiesInPendingLASList.forEach(requestItemEntity -> {
            if(requestItemEntity.getRequestStatusEntity().getRequestStatusCode().equalsIgnoreCase(ScsbConstants.REQUEST_STATUS_PENDING)){
                if(isRequestExceedsfiveMins(requestItemEntity)){
                    requestItemEntitiesInPendingList.add(requestItemEntity);
                    pendingRequestEntityList.add(getPendingRequestEntityFromRequestEntity(requestItemEntity));
                }
            }else {
                requestItemEntitiesInLASList.add(requestItemEntity);
                pendingRequestEntityList.add(getPendingRequestEntityFromRequestEntity(requestItemEntity));
            }
        });
        if(!pendingRequestEntityList.isEmpty()) {
            log.info("Identified requests stuck in PENDING/LAS");
            saveRequestPendingLASEntity(pendingRequestEntityList);
            sendEmailAndSaveList(requestItemEntitiesInPendingList, requestItemEntitiesInLASList);
            return true;
        }
        return false;
    }

    private PendingRequestEntity getPendingRequestEntityFromRequestEntity(RequestItemEntity requestItemEntity){
        PendingRequestEntity pendingRequestEntity = new PendingRequestEntity();
       // pendingRequestEntity.setId(requestItemEntity.getId());
        pendingRequestEntity.setItemId(requestItemEntity.getItemId());
        pendingRequestEntity.setRequestId(requestItemEntity.getId());
        pendingRequestEntity.setRequestCreatedDate(new Date());
        pendingRequestEntity.setRequestItemEntity(requestItemEntity);
        pendingRequestEntity.setItemEntity(requestItemEntity.getItemEntity());
        return pendingRequestEntity;
    }

    private boolean isRequestExceedsfiveMins(RequestItemEntity requestItemEntity){
        long minutes = getDifferenceInMinutes(requestItemEntity.getCreatedDate());
        return minutes > 5;
    }

    private void sendEmailAndSaveList(List<RequestItemEntity> requestItemEntitiesInPendingList, List<RequestItemEntity> requestItemEntitiesInLASList){
        String emailBody = getEmailBodyForPendinglasRequest(requestItemEntitiesInPendingList,requestItemEntitiesInLASList);
        EmailPayLoad emailPayLoad = new EmailPayLoad();
        if(!requestItemEntitiesInPendingList.isEmpty() && !requestItemEntitiesInLASList.isEmpty()){
            sendEmailNotificationForPendingRequests(emailPayLoad,emailBody, ScsbConstants.EMAIL_SUBJECT_FOR_PENDING_AND_LAS_STATUS);
        } else if(!requestItemEntitiesInPendingList.isEmpty() && requestItemEntitiesInLASList.isEmpty()){
            sendEmailNotificationForPendingRequests(emailPayLoad,emailBody, ScsbConstants.EMAIL_SUBJECT_FOR_PENDING_STATUS);
        } else if(requestItemEntitiesInPendingList.isEmpty() && !requestItemEntitiesInLASList.isEmpty()){
            sendEmailNotificationForPendingRequests(emailPayLoad,emailBody, ScsbConstants.EMAIL_SUBJECT_FOR_LAS_PENDING_STATUS);
        }
    }

    private void saveRequestPendingLASEntity(List<PendingRequestEntity> pendingRequestEntityList){
        pendingRequestDetailsRespository.saveAll(pendingRequestEntityList);
    }

    private static String getEmailBodyForPendinglasRequest(List<RequestItemEntity> pendingRequestEntities , List<RequestItemEntity> lasRequestEntities) {
        StringBuilder stringBuilder=new StringBuilder();
        if(!pendingRequestEntities.isEmpty()) {
            stringBuilder.append("Below are the request in PENDING:");
            appendItemEntityInfo(pendingRequestEntities, stringBuilder);
        }
        if(!lasRequestEntities.isEmpty()) {
            if(stringBuilder.length()>0){
                stringBuilder.append("\n\n");
            }
            stringBuilder.append("Below are the request in LAS ITEM STATUS PENDING:");
            appendItemEntityInfo(lasRequestEntities, stringBuilder);
        }
        return stringBuilder.toString();
    }

    private static void appendItemEntityInfo(List<RequestItemEntity> pendingRequestEntities, StringBuilder stringBuilder) {
        pendingRequestEntities.forEach(pendingReq -> {
            stringBuilder.append("\nBarcode : ").append(pendingReq.getItemEntity().getBarcode())
                    .append("\t\t Request Created Date : ").append(pendingReq.getCreatedDate())
                    .append("\t\t Request Type :").append(pendingReq.getRequestTypeEntity().getRequestTypeCode());
        });
    }

    private void sendEmailNotificationForPendingRequests(EmailPayLoad emailPayLoad, String message, String subject) {
        emailPayLoad.setSubject(subject);
        emailPayLoad.setMessageDisplay(message);
        emailPayLoad.setTo(pendingRequestEmailTo);
        emailPayLoad.setCc(pendingRequestEmailCc);
        producerTemplate.sendBodyAndHeader(ScsbConstants.EMAIL_Q, emailPayLoad, ScsbConstants.EMAIL_BODY_FOR, ScsbConstants.EMAIL_HEADER_REQUEST_STATUS_PENDING);
    }

    private static long getDifferenceInMinutes(Date createdDate) {
        LocalDateTime now = LocalDateTime.now();
        Instant instant = Instant.ofEpochMilli(createdDate.getTime());
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return Duration.between(localDateTime, now).toMinutes();
    }
}
