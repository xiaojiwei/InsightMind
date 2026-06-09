package com.graphinsight.indicator.util;

import com.graphinsight.indicator.auto.entity.User;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
@Slf4j
public class UserThreadLocalUtil {

    //把构造函数私有，外面不能new，只能通过下面两个方法操作
    private UserThreadLocalUtil() {
    }

    private static final ThreadLocal<User> LOCAL = new ThreadLocal<User>();
    private static final ThreadLocal<String> LOCAL_NAME = new ThreadLocal<String>();

    private static final ThreadLocal<Long> LOCAL_TIME = new ThreadLocal<Long>();

    private static final Set<String> SUPER_ADMIN = new HashSet<>();

    static {
        SUPER_ADMIN.add("anonymous");
    }

    public static void setBeginTime() {
        LOCAL_TIME.set(System.currentTimeMillis());
    }

    public static void printCost(String key) {
        Long begin = LOCAL_TIME.get();
        if (null != begin) {
            //log.info("time is {}",key + " cost:" + (System.currentTimeMillis() - begin));
            System.err.println(key + " cost:" + (System.currentTimeMillis() - begin));
        }

    }


    public static boolean isSuperAdminBack() {
        String username = getUserName();
        return SUPER_ADMIN.contains(username);
    }

    public static boolean isSuperAdminBack(String username) {
        return SUPER_ADMIN.contains(username);
    }

    public static void set(User user) {
        LOCAL.set(user);
        if (null != user) {
            LOCAL_NAME.set(user.getUsername());
        }
    }

    public static void setUserName(String userName) {
        LOCAL_NAME.set(userName);
    }

    public static User get() {
        return LOCAL.get();
    }

    public static String getUserName() {
        String string = LOCAL_NAME.get();
        if (StringUtil.isEmpty(string)) {
            string = "anonymous";
        }
        return string;
    }

    public static Integer getUserId() {
        User user = LOCAL.get();
        if (user != null) {
            return user.getId();
        }
        return null;
    }
}
