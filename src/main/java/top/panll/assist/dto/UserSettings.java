package top.panll.assist.dto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserSettings {

    @Value("${userSettings.record}")
    private String record;

    @Value("${userSettings.recordDay:7}")
    private int recordDay;

    @Value("${userSettings.recordTempDay:-1}")
    private int recordTempDay;

    @Value("${userSettings.ffmpeg}")
    private String ffmpeg;

    @Value("${userSettings.ffprobe}")
    private String ffprobe;

    public String getRecord() {
        return record;
    }

    public void setRecord(String record) {
        this.record = record;
    }

    public String getFfmpeg() {
        return ffmpeg;
    }

    public void setFfmpeg(String ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public String getFfprobe() {
        return ffprobe;
    }

    public void setFfprobe(String ffprobe) {
        this.ffprobe = ffprobe;
    }

    public int getRecordDay() {
        return recordDay;
    }

    public void setRecordDay(int recordDay) {
        this.recordDay = recordDay;
    }

    public int getRecordTempDay() {
        if (recordTempDay == -1) {
            return recordDay;
        }else {
            return recordTempDay;
        }
    }

    public void setRecordTempDay(int recordTempDay) {
        this.recordTempDay = recordTempDay;
    }
}
