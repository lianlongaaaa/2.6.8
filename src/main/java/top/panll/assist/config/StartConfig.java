package top.panll.assist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import top.panll.assist.dto.CloudRecordItem;
import top.panll.assist.mapper.CloudRecordServiceMapper;
import top.panll.assist.utils.DateUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于启动检查环境
 */
@Component
@Order(value=10)
public class StartConfig implements CommandLineRunner {

    private final static Logger logger = LoggerFactory.getLogger(StartConfig.class);

    @Value("${user-settings.record}")
    private String record;

    @Value("${user-settings.media-server-id}")
    private String mediaServerId;

    @Autowired
    DataSourceTransactionManager dataSourceTransactionManager;

    @Autowired
    TransactionDefinition transactionDefinition;

    @Autowired
    private CloudRecordServiceMapper cloudRecordServiceMapper;


    @Override
    public void run(String... args) {
        if (!record.endsWith(File.separator)) {
            record = record + File.separator;
        }

        File recordFile = new File(record);
        if (!recordFile.exists()){
            logger.warn("{}路径不存在", record);
            System.exit(1);
        }
        logger.info("开始搜集数据");
        File[] appFiles = recordFile.listFiles();
        if (appFiles == null) {
            logger.warn("{}路径下没有录像", record);
            System.exit(1);
        }
        if (appFiles.length == 0) {
            logger.warn("{}路径下没有录像", record);
            System.exit(1);
        }
        List<CloudRecordItem> cloudRecordItemList = new ArrayList<>();
        Map<String, String> renameMap = new HashMap<>();
        // 搜集数据
        for (File file : appFiles) {
            if (!file.isDirectory()) {
                continue;
            }
            String app = file.getName();
            File[] streamFiles = file.listFiles();
            if (streamFiles == null || streamFiles.length == 0) {
                continue;
            }
            for (File streamFile : streamFiles) {
                String stream = streamFile.getName();
                if ("rtp".equals(app)) {

                }else {
                    if (stream.indexOf("_") > 0) {
                        String[] streamInfoArray = stream.split("_");
                        if (streamInfoArray.length != 2) {
                            logger.warn("无法识别 {}/{}", app, stream);
                            continue;
                        }
                        stream = streamInfoArray[0];
                        String callId = streamInfoArray[1];
                        File[] dateFiles = streamFile.listFiles();
                        if (dateFiles == null || dateFiles.length == 0) {
                            continue;
                        }
                        // TODC 确定关联和归档分别使用了什么类型名称
                        boolean collect = false;
                        boolean reserve = false;
                        for (File dateFile : dateFiles) {
                            if (dateFile.isFile()) {
                                if (dateFile.getName().endsWith(".sign")) {
                                    if (dateFile.getName().startsWith("a")) {
                                        collect = true;
                                    }else if (dateFile.getName().startsWith("b")) {
                                        reserve = true;
                                    }
                                }
                            }else {
                                // 检验是否是日期格式
                                if (!DateUtils.checkDateFormat(dateFile.getName())) {
                                    continue;
                                }
                                String date = dateFile.getName();
                                File[] videoFiles = dateFile.listFiles();
                                if (videoFiles == null || videoFiles.length == 0) {
                                    continue;
                                }
                                for (int i = 0; i < videoFiles.length; i++) {
                                    File videoFile = videoFiles[i];
                                    if (!videoFile.getName().endsWith(".mp4") && !videoFile.getName().contains("-")) {
                                        continue;
                                    }
                                    String[] videoInfoArray = videoFile.getName().split("-");
                                    if (videoInfoArray.length != 3) {
                                        logger.info("非目标视频文件格式，忽略此文件： {}", videoFile.getAbsolutePath() );
                                        continue;
                                    }
                                    if (!DateUtils.checkDateTimeFormat(date + " " + videoInfoArray[0])
                                            || !DateUtils.checkDateTimeFormat(date + " " + videoInfoArray[1]) ) {
                                        logger.info("目标视频文件明明异常，忽略此文件： {}", videoFile.getName() );
                                        continue;
                                    }
                                    String startTime = date + " "  + videoInfoArray[0];
                                    String endTime = date + " " + videoInfoArray[1];
                                    Long startTimeStamp = DateUtils.yyyy_MM_dd_HH_mm_ssToTimestamp(startTime);
                                    Long endTimeStamp = DateUtils.yyyy_MM_dd_HH_mm_ssToTimestamp(endTime);

                                    long timeLength = Long.parseLong(videoInfoArray[2].substring(0, videoInfoArray[2].length() - 4));
                                    CloudRecordItem cloudRecordItem = new CloudRecordItem();
                                    cloudRecordItem.setApp(app);
                                    cloudRecordItem.setStream(stream);
                                    cloudRecordItem.setCallId(callId);
                                    cloudRecordItem.setStartTime(startTimeStamp);
                                    cloudRecordItem.setEndTime(endTimeStamp);
                                    cloudRecordItem.setCollect(collect);
                                    cloudRecordItem.setReserve(reserve);
                                    cloudRecordItem.setMediaServerId(mediaServerId);
                                    cloudRecordItem.setFileName(DateUtils.getTimeStr(startTimeStamp) + "-" + i + ".mp4");
                                    cloudRecordItem.setFolder(streamFile.getAbsolutePath());
                                    cloudRecordItem.setFileSize(videoFile.length());
                                    cloudRecordItem.setTimeLen(timeLength);
                                    cloudRecordItem.setFilePath(videoFile.getParentFile().getAbsolutePath() + File.separator + cloudRecordItem.getFileName());
                                    cloudRecordItemList.add(cloudRecordItem);
                                    renameMap.put(videoFile.getAbsolutePath(), cloudRecordItem.getFilePath());
                                    System.out.println(cloudRecordItem.getFilePath());
                                }
                            }
                        }
                    }
                }
            }
            logger.info("数据收集完成， 待处理数据为： {}条", cloudRecordItemList.size());
            logger.info("开始将数据写入数据库");
            TransactionStatus transactionStatus = dataSourceTransactionManager.getTransaction(transactionDefinition);
            int limitCount = 50;
            if (cloudRecordItemList.size() > 0) {
                if (cloudRecordItemList.size() > limitCount) {
                    for (int i = 0; i < cloudRecordItemList.size(); i += limitCount) {
                        int toIndex = i + limitCount;
                        if (i + limitCount > cloudRecordItemList.size()) {
                            toIndex = cloudRecordItemList.size();
                        }
                        int length = cloudRecordServiceMapper.batchAdd(cloudRecordItemList.subList(i, toIndex));
                        if (length == 0) {
                            dataSourceTransactionManager.rollback(transactionStatus);
                        }
                    }
                } else {
                    cloudRecordServiceMapper.batchAdd(cloudRecordItemList);
                }
                dataSourceTransactionManager.commit(transactionStatus);
            }
            logger.info("数据写入数据库完成");
        }


    }


}
