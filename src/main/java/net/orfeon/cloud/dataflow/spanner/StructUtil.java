package net.orfeon.cloud.dataflow.spanner;

import com.google.cloud.ByteArray;
import com.google.cloud.Date;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StructUtil {

    private StructUtil() {

    }

    public static Map<String,Object> toMap(Struct struct) {
        Map<String,Object> map = new HashMap<>();
        for(Type.StructField field : struct.getType().getStructFields()) {
            map.put(field.getName(), getFieldValue(field, struct));
        }
        return map;
    }

    public static String toJson(Struct struct) {
        JsonObject obj = new JsonObject();
        for(Type.StructField field : struct.getType().getStructFields()) {
            setJsonFieldValue(obj, field, struct);
        }
        return obj.toString();
    }

    public static Object getFieldValue(String fieldName, Struct struct) {
        for(Type.StructField field : struct.getType().getStructFields()) {
            if(field.getName().equals(fieldName)) {
                return getFieldValue(field, struct);
            }
        }
        return null;
    }

    private static Object getFieldValue(Type.StructField field, Struct struct) {
        switch (field.getType().getCode()) {
            case BOOL:
                return struct.getBoolean(field.getName());
            case INT64:
                return struct.getLong(field.getName());
            case FLOAT64:
                return struct.getDouble(field.getName());
            case STRING:
                return struct.getString(field.getName());
            case BYTES:
                return struct.getBytes(field.getName()).toBase64();
            case TIMESTAMP:
                return struct.getTimestamp(field.getName()).getSeconds();
            case DATE:
                return struct.getDate(field.getName());
            case STRUCT:
                Map<String,Object> map = new HashMap<>();
                Struct childStruct = struct.getStruct(field.getName());
                for (Type.StructField childField : childStruct.getType().getStructFields()) {
                    map.put(field.getName(), getFieldValue(childField, childStruct));
                }
                return map;
            case ARRAY:
                return getArrayFieldValue(field, struct);
        }
        return null;
    }

    private static Object getArrayFieldValue(Type.StructField field, Struct struct) {
        List list = new ArrayList<>();
        switch (field.getType().getArrayElementType().getCode()) {
            case BOOL:
                    struct.getBooleanList(field.getName()).stream().forEach(list::add);
                    return list;
            case INT64:
                    struct.getLongList(field.getName()).stream().forEach(list::add);
                    return list;
            case FLOAT64:
                    struct.getDoubleList(field.getName()).stream().forEach(list::add);
                    return list;
            case STRING:
                    struct.getStringList(field.getName()).stream().forEach(list::add);
                    return list;
            case BYTES:
                    struct.getBytesList(field.getName()).stream().map((ByteArray::toBase64)).forEach(list::add);
                    return list;
            case TIMESTAMP:
                    struct.getTimestampList(field.getName()).stream().map((com.google.cloud.Timestamp::getSeconds)).forEach(list::add);
                    return list;
            case DATE:
                    struct.getDateList(field.getName()).stream().map((Date date) -> date.toString()).forEach(list::add);
                    return list;
            case STRUCT:
                List<Map<String,Object>> maps = new ArrayList<>();
                for (Struct childStruct : struct.getStructList(field.getName())) {
                    Map<String,Object> map = new HashMap<>();
                    for (Type.StructField childField : childStruct.getType().getStructFields()) {
                        map.put(field.getName(), getFieldValue(childField, childStruct));
                    }
                    maps.add(map);
                }
                return maps;
            case ARRAY:
                return getArrayFieldValue(field, struct);
        }
        return null;
    }

    private static void setJsonFieldValue(JsonObject obj, Type.StructField field, Struct struct) {
        switch (field.getType().getCode()) {
            case BOOL:
                    obj.addProperty(field.getName(), struct.getBoolean(field.getName()));
                break;
            case INT64:
                    obj.addProperty(field.getName(), struct.getLong(field.getName()));
                break;
            case FLOAT64:
                    obj.addProperty(field.getName(), struct.getDouble(field.getName()));
                break;
            case STRING:
                    obj.addProperty(field.getName(), struct.getString(field.getName()));
                break;
            case BYTES:
                    obj.addProperty(field.getName(), struct.getBytes(field.getName()).toBase64());
                break;
            case TIMESTAMP:
                    obj.addProperty(field.getName(), struct.getTimestamp(field.getName()).getSeconds());
                break;
            case DATE:
                    obj.addProperty(field.getName(), struct.getDate(field.getName()).toString());
                break;
            case STRUCT:
                Struct childStruct = struct.getStruct(field.getName());
                JsonObject childObj = new JsonObject();
                for(Type.StructField childField : childStruct.getType().getStructFields()) {
                    setJsonFieldValue(childObj, childField, childStruct);
                }
                obj.add(field.getName(), childObj);
                break;
            case ARRAY:
                setJsonArrayFieldValue(obj, field, struct);
                break;
        }
    }

    private static void setJsonArrayFieldValue(JsonObject obj, Type.StructField field, Struct struct) {
        JsonArray array = new JsonArray();
        switch (field.getType().getArrayElementType().getCode()) {
            case BOOL:
                struct.getBooleanList(field.getName()).stream().forEach(array::add);
                obj.add(field.getName(), array);
                break;
            case INT64:
                struct.getLongList(field.getName()).stream().forEach(array::add);
                obj.add(field.getName(), array);
                break;
            case FLOAT64:
                struct.getDoubleList(field.getName()).stream().forEach(array::add);
                obj.add(field.getName(), array);
                break;
            case STRING:
                struct.getStringList(field.getName()).stream().forEach(array::add);
                obj.add(field.getName(), array);
                break;
            case BYTES:
                struct.getBytesList(field.getName()).stream().map(ByteArray::toBase64).forEach(array::add);
                obj.add(field.getName(), array);
                break;
            case TIMESTAMP:
                struct.getTimestampList(field.getName()).stream().map(com.google.cloud.Timestamp::getSeconds).forEach(array::add);
                obj.add(field.getName(), array);
                break;
            case DATE:
                struct.getDateList(field.getName()).stream().map((Date date) -> date.toString()).forEach(array::add);
                obj.add(field.getName(), array);
                break;
            case STRUCT:
                for(Struct childStruct : struct.getStructList(field.getName())) {
                    JsonObject childObj = new JsonObject();
                    for(Type.StructField childField : childStruct.getType().getStructFields()) {
                        setJsonFieldValue(childObj, childField, childStruct);
                    }
                    array.add(childObj);
                }
                obj.add(field.getName(), array);
                break;
            case ARRAY:
                setJsonArrayFieldValue(obj, field, struct);
                break;
        }
    }

}
