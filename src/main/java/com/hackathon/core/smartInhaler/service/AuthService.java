package com.hackathon.core.smartInhaler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.core.smartInhaler.exception.ApplicationException;
import com.hackathon.core.smartInhaler.model.LoginRequest;
import com.hackathon.core.smartInhaler.util.Constant;
import com.hackathon.core.smartInhaler.util.ModuleUrls;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AuthService {

    private static ObjectMapper mapper = new ObjectMapper();
    @Value("${app.context.path}")
    private String appContext;
    @Value("${app.solution.key}")
    private String solutionKey;
    @Autowired
    private RestTemplate restTemplate;

    public ResponseEntity getBasicToken() {
        log.info("Inside AuthService.getBasicToken");
        String url = ModuleUrls.authUrl + "/basic-token";
        return restTemplate.getForEntity(url, String.class);
    }

    /**
     *
     * @param authorization
     * @param loginRequest
     * @return
     * @throws IOException
     * @throws ApplicationException
     */
    public ResponseEntity login(String authorization, LoginRequest loginRequest) throws IOException, ApplicationException {
        log.info("Inside AuthService.login");
        String loginUrl = ModuleUrls.authUrl + "/login";
        Map<String, Object> jsonResp = new HashMap<>();
        HttpHeaders headers1 = new HttpHeaders();
        headers1.add("authorization", String.format("Basic %s", authorization));
        headers1.add("solution-key", solutionKey);
        headers1.setContentType(MediaType.APPLICATION_JSON_UTF8);
        HttpEntity<LoginRequest> entity1 = new HttpEntity(loginRequest, headers1);

        ResponseEntity response = restTemplate.postForEntity(loginUrl, entity1, String.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            String userIdentityUrl = ModuleUrls.userUrl + "/identity";
            HttpHeaders headers2 = new HttpHeaders();
            String respBody = (String) response.getBody();
            jsonResp = mapper.readValue(respBody, Map.class);
            headers2.add("authorization", String.format("Bearer %s", jsonResp.get("access_token")));
            headers2.setContentType(MediaType.APPLICATION_JSON_UTF8);
            HttpEntity entity2 = new HttpEntity(headers2);

            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(userIdentityUrl).queryParam("permissions", true);
            ResponseEntity userIdentifyResp = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity2, String.class);
            String resp = (String) userIdentifyResp.getBody();
            Map<String, Object> jsonRespUserIdentity = mapper.readValue(resp, Map.class);
            ResponseEntity userInfo = restTemplate.exchange(ModuleUrls.userUrl + "/" + ((Map) jsonRespUserIdentity.get("data")).get("userGuid"), HttpMethod.GET, entity2, String.class);
            String respBody1 = (String) userInfo.getBody();
            Map<String, Object> jsonResp1 = mapper.readValue(respBody1, Map.class);
            ((Map) jsonRespUserIdentity.get("data")).put("email", ((List<Map<String, Object>>) jsonResp1.get("data")).get(0).get("userId"));
            ((Map) jsonRespUserIdentity.get("data")).put("contactNo", ((List<Map<String, Object>>) jsonResp1.get("data")).get(0).get("contactNo"));
            if (jsonRespUserIdentity.get("data") != null && ((Map) jsonRespUserIdentity.get("data")).get("permissions") != null
                    && ((Map) (((Map) jsonRespUserIdentity.get("data")).get("permissions"))).get("roleName") != null) {
                String roleName = ((Map) (((Map) jsonRespUserIdentity.get("data")).get("permissions"))).get("roleName").toString();
                if (roleName.equalsIgnoreCase("Doctor")) {
                    throw new ApplicationException("You are not allowed to view this site");
                } else {
                    jsonResp.put("data", jsonRespUserIdentity.get("data"));
                }
            }
        }
        return new ResponseEntity(jsonResp, HttpStatus.OK);

    }

    /**
     *
     * @param authorization
     * @param userGuid
     * @return
     * @throws IOException
     * @throws ParseException
     */
    public ResponseEntity getUserDashboardDetails(String authorization, String userGuid) throws IOException, ParseException {
        log.info("Inside AuthService.getUserDashboardDetails");
        Map<String, Object> map = getUserDetails(authorization, userGuid);
        if(((double)map.get("Temp") <= 18 || (double)map.get("Temp") >= 35)
            && ((Integer)map.get("Humidity") <= 30 || (Integer)map.get("Humidity") >= 60)) {
            map.put("Comfortable", false);
        } else{
            map.put("Comfortable", true);
        }
        //set next Dose time
        if(!"missed".equals(map.get("LastDose").toString())) {
            map.put("NextDose", getNextDoseTime(map.get("LastDose").toString(), map.get("Gap").toString()));
        } else{
            map.put("NextDose", "Now");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("data", map);
        return new ResponseEntity<>(data, HttpStatus.OK);

    }

    /**
     *
     * @param lastDose
     * @param gap
     * @return
     * @throws ParseException
     */
    private String getNextDoseTime(String lastDose, String gap) throws ParseException {
        log.info("Inside AuthService.getNextDoseTime");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        Calendar cal = Calendar.getInstance(); // creates calendar
        cal.setTime(getParsedDate(lastDose)); // sets calendar time/date
        cal.add(Calendar.HOUR_OF_DAY, Integer.parseInt(gap)); // adds one hour
        Date nextDoseTime = cal.getTime();
        return format.format(nextDoseTime);
    }

    /**
     *
     * @param authorization
     * @param userGuid
     * @return
     * @throws IOException
     */
    public Map<String, Object> getUserDetails(String authorization, String userGuid) throws IOException {
        log.info("Inside AuthService.getUserDetails");
        String url = ModuleUrls.userUrl + "/" + userGuid;
        String deviceUrl = ModuleUrls.deviceUrl + "/user/" + userGuid;
        String telemetryUrl = ModuleUrls.telemetryUrl + "/device/";
        HttpHeaders headers = new HttpHeaders();
        headers.add("authorization", String.format("Bearer %s", authorization));
        headers.add("solution-key", solutionKey);
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
        HttpEntity<LoginRequest> entity = new HttpEntity(headers);
        ResponseEntity userResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        String respBody = (String) userResponse.getBody();
        Map<String, Object> jsonResp = mapper.readValue(respBody, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) jsonResp.get("data");
        List<Map<String, Object>> list1 = (List<Map<String, Object>>) list.get(0).get("userInfo");
        Map<String, Object> map1 = new HashMap<>();
        for (Map<String, Object> map : list1) {
            if (map.get("name").toString().contains("Gap between two dosages(Hrs)")) {
                map1.put("Gap", map.get("value").toString());
            }
            if (map.get("name").toString().contains("Number of times/Day")) {
                map1.put("TimesPerDay", map.get("value"));
            }
            if (map.get("name").toString().contains("Select Days")) {
                map1.put("Days", map.get("value"));
            }
            map1.put("Adherence", true);
        }

        ResponseEntity deviceResponse = restTemplate.exchange(deviceUrl, HttpMethod.GET, entity, String.class);
        String deviceRes = deviceResponse.getBody().toString();
        Map<String, Object> deviceRespMap = mapper.readValue(deviceRes, Map.class);
        List<Map<String, Object>> deviceList = (List<Map<String, Object>>) deviceRespMap.get("data");
        if(!CollectionUtils.isEmpty(deviceList)) {
            Map<String, Object> device = deviceList.get(0);
            String guid = device.get("guid").toString();
            String uniqueId = device.get("uniqueId").toString();
            ResponseEntity telemetryResponse = restTemplate.exchange(telemetryUrl + guid, HttpMethod.GET, entity, String.class);
            String telemetryResponseBody = (String) telemetryResponse.getBody();
            Map<String, Object> teleMap = mapper.readValue(telemetryResponseBody, Map.class);
            List<Map<String, Object>> teleList = (List<Map<String, Object>>) teleMap.get("data");
            for (Map<String, Object> map : teleList) {
                if (map.get("attributeName").toString().contains("dosage")) {
                    map1.put("Dosage", map.get("attributeValue"));
                }
                if (map.get("attributeName").toString().contains("humidity")) {
                    map1.put("Humidity", Integer.valueOf(map.get("attributeValue").toString()));
                }
                if (map.get("attributeName").toString().contains("temp")) {
                    map1.put("Temp", Double.valueOf(map.get("attributeValue").toString()));
                }
            }

            //get doctor details
            getDoctorDetails(map1, entity, userGuid);
            map1.put("SpraysLeft", 0);
            map1.put("NextChange", "");

            //get inhaler details
            getMyInhalerDetails(map1, entity, authorization, uniqueId);
        } else{
            log.error("No device alotted to user");
        }
        return map1;
    }

    /**
     *
     * @param map1
     * @param entity
     * @param authorization
     * @param uniqueId
     * @throws IOException
     */
    private void getMyInhalerDetails(Map<String, Object> map1, HttpEntity<LoginRequest> entity, String authorization, String uniqueId) throws IOException {
        log.info("Inside AuthService.getMyInhalerDetails");
        String deviceUrl = ModuleUrls.deviceUrl + "/uniqueId/" + uniqueId;
        ResponseEntity deviceResponse = restTemplate.exchange(deviceUrl, HttpMethod.GET, entity, String.class);
        String deviceRes = deviceResponse.getBody().toString();
        Map<String, Object> deviceRespMap = mapper.readValue(deviceRes, Map.class);
        List<Map<String, Object>> devicePropertiesList = (List<Map<String, Object>>) ((List<Map<String, Object>>) deviceRespMap.get("data")).get(0).get("properties");
        for (Map<String, Object> map : devicePropertiesList) {
            if (map.get("name").toString().contains("Date_Time_Changed")) {
                String lastChangeDate = map.get("value") != null ? map.get("value").toString() : null;
                map1.put("LastChange",lastChangeDate);
            }
            if (map.get("name").toString().contains("Number of Sprays")) {
                int spraysConsumed = calculateSpraysConsumed(authorization, map1);
                if(map.get("value") != null) {
                    int spraysLeft = Integer.valueOf(map.get("value").toString()) - spraysConsumed;
                    map1.put("SpraysLeft", spraysLeft);
                }
            }

        }
    }

    /**
     *
     * @param lastChangeDate
     * @return
     * @throws ParseException
     */
    private Date getParsedDate(String lastChangeDate) throws ParseException {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date date = dateFormat.parse(lastChangeDate);//You will get date object relative to server/client timezone wherever it is parsed
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //If you need time just put specific format for time like 'HH:mm:ss'
        String date1tr = formatter.format(date);
        System.out.println(date1tr);
        return date;
    }

    /**
     *
     * @param authorization
     * @param map1
     * @return
     * @throws IOException
     */
    private int calculateSpraysConsumed(String authorization, Map<String, Object> map1) throws IOException {
        log.info("Inside AuthService.calculateSpraysConsumed");
        ResponseEntity dosageHistoryResp = getAttributeDetails(authorization, setStartDate(), setCurrentDate(), Constant.deviceUniqueId);
        String historyResp = dosageHistoryResp.getBody().toString();
        Map<String, Object> deviceRespMap = mapper.readValue(historyResp, Map.class);
        LinkedHashMap map = (LinkedHashMap) deviceRespMap.get("data");
        ArrayList<LinkedHashMap> list = (ArrayList<LinkedHashMap>) map.get("feed");
        Integer spraysConsumed = list.stream().map(x -> x.containsKey("dosage") && x.get("dosage") != null && !x.get("dosage").equals("") ? Integer.parseInt(x.get("dosage").toString()) : null).filter(x -> x != null).max(Integer::compare).get();
        String currentDate = setCurrentDate();
        //set last dose
        List<Map> maxSpraysMap = list.stream().filter(x -> spraysConsumed.equals(x.get("dosage"))).collect(Collectors.toList());
        String lastUpdateSprayTime = maxSpraysMap.get(0) != null ? maxSpraysMap.get(0).get("dTime") != null ? maxSpraysMap.get(0).get("dTime").toString() : "2019-08-26 18:00:00" : "2019-08-26 18:00:00";
        if (!StringUtils.isEmpty(lastUpdateSprayTime)) {
            map1.put("LastDose", lastUpdateSprayTime);
        }
        return spraysConsumed;
    }

    /**
     *
     * @return
     */
    public String setStartDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        return sdf.format(now.getTime());
    }

    /**
     *
     * @return
     */
    public String setCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar now = Calendar.getInstance();
        return sdf.format(now.getTime());

    }

    /**
     *
     * @param map1
     * @param entity
     * @param userGuid
     * @return
     * @throws IOException
     */
    private Map<String, Object> getDoctorDetails(Map<String, Object> map1, HttpEntity<LoginRequest> entity, String userGuid) throws IOException {
        //get patient entityguid
        log.info("Inside AuthService.getDoctorDetails");
        ResponseEntity userInfo = restTemplate.exchange(ModuleUrls.userUrl + "/" + userGuid, HttpMethod.GET, entity, String.class);
        String userResponseBody = (String) userInfo.getBody();
        Map<String, Object> userMap = mapper.readValue(userResponseBody, Map.class);
        String entityGuid = ((List<Map<String, Object>>) userMap.get("data")).get(0).get("entityGuid").toString();
        //get defaultuserGuid (who is doctor)
        ResponseEntity userEntityInfo = restTemplate.exchange(ModuleUrls.userEntityUrl + "/" + entityGuid, HttpMethod.GET, entity, String.class);
        String userEntityResponseBody = (String) userEntityInfo.getBody();
        Map<String, Object> userEntityMap = mapper.readValue(userEntityResponseBody, Map.class);
        String defaultuserGuid = ((List<Map<String, Object>>) userEntityMap.get("data")).get(0).get("defaultuserGuid").toString();
        // get doctor info
        ResponseEntity defaultUserInfo = restTemplate.exchange(ModuleUrls.userUrl + "/" + defaultuserGuid, HttpMethod.GET, entity, String.class);
        String defaultUserResponseBody = (String) defaultUserInfo.getBody();
        Map<String, Object> defaultUserMap = mapper.readValue(defaultUserResponseBody, Map.class);
        Map<String, Object> doctorInfo = ((List<Map<String, Object>>) defaultUserMap.get("data")).get(0);
        map1.put("DoctorName", doctorInfo.get("firstName").toString() + " " + doctorInfo.get("lastName").toString());
        map1.put("DoctorRole", doctorInfo.get("roleName").toString());
        map1.put("DoctorContact", doctorInfo.get("contactNo").toString());
        return map1;

    }

    /**
     *
     * @param authorization
     * @param fromDate
     * @param toDate
     * @param uniqueId
     * @return
     */
    public ResponseEntity getAttributeDetails(String authorization, String fromDate, String toDate, String uniqueId) {
        log.info("Inside AuthService.getAttributeDetails");
        String attributeUrl = ModuleUrls.telemetryUrl + "/attribute-history/attribute/{attributeGuid}/device/{uniqueId}/from/{fromDate}/to/{toDate}";
        HttpHeaders headers = new HttpHeaders();
        headers.add("authorization", String.format("Bearer %s", authorization));
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("attributeGuid", "dosage");
        pathParams.put("uniqueId", uniqueId);
        pathParams.put("fromDate", fromDate);
        pathParams.put("toDate", toDate);
        HttpEntity entity = new HttpEntity(headers);
        return  restTemplate.exchange(attributeUrl, HttpMethod.GET, entity, String.class, pathParams);
    }

}
