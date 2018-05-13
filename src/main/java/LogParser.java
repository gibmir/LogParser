
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author pavelUsachev
 */
public class LogParser {
    // Шаблоны для поиска информации в логах
    private final static String METHOD_INFO_PATTERN = "(\\d{4})-(\\d{2})-(\\d{2})(T{1})(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3,})(\\p{Blank})TRACE(\\p{Blank})(\\[)(\\w*)(])(\\p{Blank})(entry|exit)(\\p{Blank})with(\\p{Blank})\\((\\w*):(\\d+)";
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

    // Считывает файл и помещает в StringBuilder
    private static StringBuilder fileRead(String filePath) {
        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            reader.lines().forEach(fileContent::append);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContent;
    }

    // Находит строки с конкретной информацией о вызове метода
    private static List<String> findInfo(StringBuilder logFile) {
        Pattern methodInfoPattern = Pattern.compile(METHOD_INFO_PATTERN);
        Matcher methodInfoMatcher = methodInfoPattern.matcher(logFile);
        List<String> fileInfo = new ArrayList<>();
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
        if(methodTimeMatcher.find()){
            try {
                time = dateFormatter.parse(methodTimeMatcher.group());
                ms = time.getTime();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return ms;
    }

    // Находит название метода
    private static StringBuilder findMethodName(String methodInfo) {
        StringBuilder methodName = new StringBuilder();
        Matcher methodNameMatcher = methodNamePattern.matcher(methodInfo);
        if(methodNameMatcher.find()){
            methodName.append(methodNameMatcher.group().replaceAll(SYMBOLS_PATTERN, ""));
        }
        return methodName;
    }

    // Находит id вызова
    private static int findMethodId(String methodInfo) {
        int methodID = 0;
        Matcher methodIdMatcher = methodIDPattern.matcher(methodInfo);
        if(methodIdMatcher.find()){
            methodID = Integer.valueOf(methodIdMatcher.group().replaceAll(NUMBERS_PATTERN, ""));
        }
        return methodID;
    }

    // Создаёт карту, содержащую в себе структурированную информацию о методах
    private static Map<String, Map<Integer, Long>> createMap(List<String> logInfo) {
        Map<String, Map<Integer, Long>> logMap = new HashMap<>();
        for (String methodInfo : logInfo) {
            putMethodInfo(logMap, methodInfo);
        }
        return logMap;
    }

    private static void putMethodInfo(Map<String, Map<Integer, Long>> logMap, String methodInfo) {
        String methodName = findMethodName(methodInfo).toString();
        int methodId = findMethodId(methodInfo);
        long methodTime = findMethodTime(methodInfo);
        long workTime;
        if (isMethodInfoAlreadyContain(logMap, methodName)) {
            if (isMethodEntryInfo(logMap, methodName, methodId)) {
                logMap.get(methodName).put(methodId, methodTime);
            } else {
                workTime = methodTime - logMap.get(methodName).get(methodId);
                logMap.get(methodName).put(methodId, workTime);
            }
        } else {
            Map<Integer, Long> callMap = new HashMap<>();
            callMap.put(methodId, methodTime);
            logMap.put(methodName, callMap);
        }
    }

    private static boolean isMethodInfoAlreadyContain(Map<String, Map<Integer, Long>> logMap, String methodName) {
        return logMap.containsKey(methodName);
    }

    private static boolean isMethodEntryInfo(Map<String, Map<Integer, Long>> logMap, String methodName, int methodId) {
        return !logMap.get(methodName).containsKey(methodId);
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
    // Находит минимальное время работы метода

    private static long minCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        return logMap.get(methodName).values().stream()
                .min(Long::compareTo)
                .orElseThrow(NullPointerException::new);
    }

    // Находит максимальное время работы метода
    private static long maxCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        return logMap.get(methodName).values().stream().
                max(Long::compareTo).
                orElseThrow(NullPointerException::new);
    }

    // Находит среднее время работы метода
    private static double averageCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        Map<Integer, Long> methodCallInfo = logMap.get(methodName);
        return methodCallInfo.values().stream().
                map(Long::doubleValue).
                reduce(0.0, (averageCallTime, methodCallTime) -> averageCallTime += methodCallTime / methodCallInfo.values().size());
    }

    // Находит количество вызовов метода
    private static int countCall(Map<String, Map<Integer, Long>> logMap, String methodName) {
        return logMap.get(methodName).size();
    }

    // Находит ID самого долгого вызова
    private static int maxID(Map<String, Map<Integer, Long>> logMap, String methodName) {
        return logMap.get(methodName).entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .orElseThrow(NullPointerException::new)
                .getKey();
    }

    public static void main(String... args) {
        String filePath = args[0];
        StringBuilder logFile = fileRead(filePath);
        List<String> logInfo = findInfo(logFile);
        Map<String, Map<Integer, Long>> calcMap = createMap(logInfo);
        representResult(calcMap);
    }
}
