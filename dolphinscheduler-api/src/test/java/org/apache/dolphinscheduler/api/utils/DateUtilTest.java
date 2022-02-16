package org.apache.dolphinscheduler.api.utils;

import java.time.ZoneId;
import java.util.Date;

public class DateUtilTest {

    public static void main(String[] args) {

        Date date = new Date();
        System.out.println(date.getTime());

        System.out.println(ZoneId.getAvailableZoneIds());

    }
}
