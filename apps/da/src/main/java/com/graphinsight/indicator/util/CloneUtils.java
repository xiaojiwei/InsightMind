package com.graphinsight.indicator.util;

import java.io.*;

public class CloneUtils {

    /**
     * 深拷贝对象
     * @param obj
     * @param <T>
     * @return
     */
    public static <T extends Serializable>  T clone(T obj) {

        // 拷贝产生的对象
        T clonedObj = null;
        try {

            // 读取对象字节数据
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.close();

            // 分配内存空间，写入原始对象，生成新对象
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);

            //返回新对象，并做类型转换
            clonedObj = (T)ois.readObject();
            ois.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return clonedObj;

    }

}
