package org.recap.service.purge;

import org.recap.ReCAPConstants;
import org.recap.model.RequestTypeEntity;
import org.recap.repository.RequestItemDetailsRepository;
import org.recap.repository.RequestTypeDetailsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Created by hemalathas on 13/4/17.
 */
@Service
public class PurgeService {

    private static final Logger logger = LoggerFactory.getLogger(PurgeService.class);

    @Value("${purge.email.address.edd.request.day.limit}")
    private Integer purgeEmailEddRequestDayLimit;

    @Value("${purge.email.address.physical.request.day.limit}")
    private Integer purgeEmailPhysicalRequestDayLimit;

    @Value("${purge.exception.request.day.limit}")
    private Integer purgeExceptionRequestDayLimit;

    @Autowired
    private RequestItemDetailsRepository requestItemDetailsRepository;

    @Autowired
    private RequestTypeDetailsRepository requestTypeDetailsRepository;

    public Map<String,Integer> purgeEmailAddress(){
        List<RequestTypeEntity> requestTypeEntityList = requestTypeDetailsRepository.findAll();
        List<Integer> physicalRequestTypeIdList = new ArrayList<>();
        List<Integer> eddRequestTypeIdList = new ArrayList();
        for(RequestTypeEntity requestTypeEntity : requestTypeEntityList){
            if(requestTypeEntity.getRequestTypeCode().equals(ReCAPConstants.EDD_REQUEST)){
                eddRequestTypeIdList.add(requestTypeEntity.getRequestTypeId());
            }else{
                physicalRequestTypeIdList.add(requestTypeEntity.getRequestTypeId());
            }
        }
        int noOfUpdatedRecordsForEddRequest = requestItemDetailsRepository.purgeEmailId(eddRequestTypeIdList,new Date(), purgeEmailEddRequestDayLimit);
        int noOfUpdatedRecordsForPhysicalRequest = requestItemDetailsRepository.purgeEmailId(physicalRequestTypeIdList,new Date(), purgeEmailPhysicalRequestDayLimit);
        Map<String,Integer> responseMap = new HashMap<>();
        responseMap.put(ReCAPConstants.PURGE_EDD_REQUEST , noOfUpdatedRecordsForEddRequest);
        responseMap.put(ReCAPConstants.PURGE_PHYSICAL_REQUEST , noOfUpdatedRecordsForPhysicalRequest);
        return responseMap;
    }

    public Map<String, String> purgeExceptionRequests() {
        Map<String, String> responseMap = new HashMap<>();
        try {
            Integer countOfPurgedExceptionRequests = requestItemDetailsRepository.purgeExceptionRequests(ReCAPConstants.REQUEST_STATUS_EXCEPTION, new Date(), purgeExceptionRequestDayLimit);
            logger.info("Total number of exception requests purged : {}", countOfPurgedExceptionRequests);
            responseMap.put(ReCAPConstants.STATUS, ReCAPConstants.SUCCESS);
            responseMap.put(ReCAPConstants.COUNT_OF_PURGED_EXCEPTION_REQUESTS, String.valueOf(countOfPurgedExceptionRequests));
        } catch (Exception exception) {
            logger.error(ReCAPConstants.LOG_ERROR, exception);
            responseMap.put(ReCAPConstants.STATUS, ReCAPConstants.FAILURE);
            responseMap.put(ReCAPConstants.MESSAGE, exception.getMessage());
        }
        return responseMap;
    }
}