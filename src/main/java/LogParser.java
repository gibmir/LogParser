
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class LogParser {
    // Шаблоны для поиска информации в логах
    private final static String METHOD_INFO_PATTERN = "(\\d{4})-(\\d{2})-(\\d{2})(T{1})(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3,})(\\p{Blank})TRACE(\\p{Blank})(\\[)(\\w*)(\\])(\\p{Blank})(entry|exit)(\\p{Blank})with(\\p{Blank})\\((\\w*):(\\d+)";
    private final static String METHOD_TIME_PATTERN = "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})";
    private final static String METHOD_NAME_PATTERN = "\\((\\w*):";
    private final static String METHOD_ID_PATTERN = "\\((\\w*):(\\d*)";
    private final static String DATE_PATTERN = "hh:mm:ss,S";
    private final static String SYMBOLS_PATTERN = "\\W*";
    private final static String NUMBERS_PATTERN = "\\D*";
    private static Pattern methodTimePattern = Pattern.compile(METHOD_TIME_PATTERN);
    private static SimpleDateFormat dateFormatter = new SimpleDateFormat(DATE_PATTERN);
    private static Pattern methodNamePattern = Pattern.compile(METHOD_NAME_PATTERN);
    private static Pattern methodIDPattern = Pattern.compile(METHOD_ID_PATTERN);
    private static StringBuilder logFile;
    private static List<String> logInfo;

    // Считывает файл и помещает в StringBuilder
    private static StringBuilder fileRead(String filePath) {
        StringBuilder fileContent = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            String tmp;
            while ((tmp = br.readLine()) != null) {
                fileContent.append(tmp);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContent;
    }

    // Находит строки с конкретной информацией о вызове метода
    private static List<String> findInfo(StringBuilder logFile) {
        Pattern methodInfoPattern = Pattern.compile(METHOD_INFO_PATTERN);
        Matcher methodInfoMatcher = methodInfoPattern.matcher(logFile);
        List<String> fileInfo = new ArrayList<String>();
        while (methodInfoMatcher.find()) {
            fileInfo.add(methodInfoMatcher.group());
        }
        return fileInfo;
    }

    // Находит время вызова и преобразует его в Date, затем вычисляет время в
    // миллисекундах
    private static long findMethodTime(String methodInfo) {
        long ms = 0;
        Date time;
        Matcher methodTimeMatcher = methodTimePattern.matcher(methodInfo);
        methodTimeMatcher.find();
        try {
            time = dateFormatter.parse(methodTimeMatcher.group());
            ms = time.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return ms;
    }

    // Находит название метода
    private static StringBuilder findMethodName(String methodInfo) {
        StringBuilder methodName = new StringBuilder();
        Matcher methodNameMatcher = methodNamePattern.matcher(methodInfo);
        methodNameMatcher.find();
        methodName.append(methodNameMatcher.group().replaceAll(SYMBOLS_PATTERN, ""));
        return methodName;
    }

    // Находит id вызова
    private static int findMethodID(String methodInfo) {
        int methodID;
        Matcher methodIdMatcher = methodIDPattern.matcher(methodInfo);
        methodIdMatcher.find();
        methodID = Integer.valueOf(methodIdMatcher.group().replaceAll(NUMBERS_PATTERN, ""));
        return methodID;
    }

    // Создаёт карту, содержащую в себе структурированную информацию о методах
    private static Map<String, Map<Integer, Long>> createMap(List<String> logInfo) {
        String methodName;
        int methodID;
        long methodTime;
        long workTime;
        Map<String, Map<Integer, Long>> logMap = new HashMap<String, Map<Integer, Long>>();
        for (String methodInfo : logInfo) {
            // Находим имя метода
            methodName = findMethodName(methodInfo).toString();
            // Находим ID метода
            methodID = findMethodID(methodInfo);
            // Находим время вызова метода
            methodTime = findMethodTime(methodInfo);
            // Если у нас уже имеется информация о методе с таким названием
            if (logMap.containsKey(methodName)) {
                // Если у нас уже имеется информация о методе с таким названием
                // и таким ID
                if (logMap.get(methodName).containsKey(methodID)) {
                    // Если не имеется времени входа в метод
                    if (logMap.get(methodName).get(methodID).equals(null)) {
                        logMap.get(methodName).put(methodID, methodTime);
                    } else {
                        workTime = methodTime - logMap.get(methodName).get(methodID).longValue();
                        logMap.get(methodName).put(methodID, workTime);
                    }
                } else {
                    logMap.get(methodName).put(methodID, methodTime);
                }
            } else {
                Map<Integer, Long> callMap = new HashMap<Integer, Long>();
                callMap.put(methodID, methodTime);
                logMap.put(methodName.toString(), callMap);
            }

        }
        return logMap;
    }

    // Находит максимальное время работы метода
    private static long maxCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        long max = 0;
        max = Collections.max(logMap.get(methodName).values());
        return max;
    }

    // Находит минимальное время работы метода
    private static long minCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        long min = 0;
        min = Collections.min(logMap.get(methodName).values());
        return min;
    }

    // Находит среднее время работы метода
    private static float averageCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        float average = 0;
        for (Integer methodID : logMap.get(methodName).keySet()) {
            average += logMap.get(methodName).get(methodID);
        }
        average /= logMap.get(methodName).values().size();
        return average;
    }

    // Находит количество вызовов метода
    private static int countCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        int count = 0;
        count = logMap.get(methodName).size();
        return count;
    }

    // Находит ID самого долгого вызова
    private static int maxID(Map<String, Map<Integer, Long>> logMap, String methodName) {
        int maxID = 0;
        long maxTime = maxCall(logMap, methodName);
        for (Integer methodID : logMap.get(methodName).keySet()) {
            if (logMap.get(methodName).get(methodID).equals(maxTime)) {
                maxID = methodID;
            }
        }
        return maxID;
    }

    // Отображает результаты
    private static void representResult(Map<String, Map<Integer, Long>> logMap) {
        for (String methodName : logMap.keySet()) {
            System.out.printf(
                    "Method name: %s; Minimum call time: %dms; Maximum call time: %dms; Average call time: %.2fms; Number of calls: %d; ID of maximum call time: %d.%n",
                    methodName, minCall(logMap, methodName), maxCall(logMap, methodName),
                    averageCall(logMap, methodName), countCall(logMap, methodName), maxID(logMap, methodName));
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        String filePath = args[0];
        logFile = fileRead(filePath);
        logInfo = findInfo(logFile);
        Map<String, Map<Integer, Long>> calcMap = createMap(logInfo);
        representResult(calcMap);
    }
}
