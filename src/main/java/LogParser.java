
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.chrono.ChronoZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Pavel Usachev
 */
public class LogParser {
    private final static String METHOD_INFO_PATTERN = "(\\d{4})-(\\d{2})-(\\d{2})(T)(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3,})(\\p{Blank})TRACE(\\p{Blank})(\\[)(\\w*)(])(\\p{Blank})(entry|exit)(\\p{Blank})with(\\p{Blank})\\((\\w*):(\\d+)";
    private final static String METHOD_NAME_PATTERN = "\\((\\w*):";
    private final static String METHOD_ID_PATTERN = "\\((\\w*):(\\d*)";
    private final static String LOCAL_DATE_TIME_PATTERN = "(\\d{4,})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2}(?:,\\d+)?))";
    private final static String SYMBOLS_PATTERN = "\\W*";
    private final static String NUMBERS_PATTERN = "\\D*";

    private static Pattern methodCallTimePattern = Pattern.compile(LOCAL_DATE_TIME_PATTERN);
    private static Pattern methodNamePattern = Pattern.compile(METHOD_NAME_PATTERN);
    private static Pattern methodIDPattern = Pattern.compile(METHOD_ID_PATTERN);
    private static Pattern methodInfoPattern = Pattern.compile(METHOD_INFO_PATTERN);

    public static void main(String... args) {
        Map<String, Map<Integer, Long>> resultMap = initWith(LogParser::readFile)
                .andThen(LogParser::findMethodInfo)
                .andThen(LogParser::createLogInfoMap)
                .apply(args[0]);
        representResult(resultMap);
    }

    private static StringBuilder readFile(String filePath) {
        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            reader.lines().forEach(fileContent::append);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileContent;
    }

    private static List<String> findMethodInfo(StringBuilder logFile) {
        Matcher methodInfoMatcher = methodInfoPattern.matcher(logFile);
        List<String> fileInfo = new ArrayList<>();
        while (methodInfoMatcher.find()) {
            fileInfo.add(methodInfoMatcher.group());
        }
        return fileInfo;
    }

    private static Map<String, Map<Integer, Long>> createLogInfoMap(List<String> logInfo) {
        Map<String, Map<Integer, Long>> logMap = new HashMap<>();
        for (String methodInfo : logInfo) {
            putMethodInfo(logMap, methodInfo);
        }
        return logMap;
    }

    private static void putMethodInfo(Map<String, Map<Integer, Long>> logMap, String methodInfo) {
        String methodName = findMethodName(methodInfo).toString();
        if (isMethodInfoAlreadyContains(logMap, methodName)) {
            updateMethodCallInfoMap(logMap.get(methodName), methodInfo);
        } else {
            logMap.put(methodName, createMethodCallInfoMap(methodInfo));
        }
    }

    private static boolean isMethodInfoAlreadyContains(Map<String, Map<Integer, Long>> logMap, String methodName) {
        return logMap.containsKey(methodName);
    }

    private static void updateMethodCallInfoMap(Map<Integer, Long> methodCallInfo, String methodInfo) {
        int methodId = findMethodId(methodInfo);
        long methodTime = findMethodTime(methodInfo);
        long workTime;
        if (isMethodEntryInfo(methodCallInfo, methodId)) {
            methodCallInfo.put(methodId, methodTime);
        } else {
            workTime = methodTime - methodCallInfo.get(methodId);
            methodCallInfo.put(methodId, workTime);
        }
    }

    private static Map<Integer, Long> createMethodCallInfoMap(String methodInfo) {
        Map<Integer, Long> methodCallInfoMap = new HashMap<>();
        methodCallInfoMap.put(findMethodId(methodInfo), findMethodTime(methodInfo));
        return methodCallInfoMap;
    }

    private static boolean isMethodEntryInfo(Map<Integer, Long> methodInfo, int methodCallId) {
        return !methodInfo.containsKey(methodCallId);
    }

    private static StringBuilder findMethodName(String methodInfo) {
        StringBuilder methodName = new StringBuilder();
        Matcher methodNameMatcher = methodNamePattern.matcher(methodInfo);
        if (methodNameMatcher.find()) {
            methodName.append(methodNameMatcher.group().replaceAll(SYMBOLS_PATTERN, ""));
        }
        return methodName;
    }

    private static int findMethodId(String methodInfo) {
        Matcher methodIdMatcher = methodIDPattern.matcher(methodInfo);
        int methodID = -1;
        if (methodIdMatcher.find()) {
            methodID = Integer.valueOf(methodIdMatcher.group().replaceAll(NUMBERS_PATTERN, ""));
        }
        return methodID;
    }

    private static long findMethodTime(String methodInfo) {
        long ms = 0;
        Matcher methodLocalDateMatcher = methodCallTimePattern.matcher(methodInfo);
        if (methodLocalDateMatcher.find()) {
            ms = parseMatchedMethodCallTime(methodLocalDateMatcher.group());
        }
        return ms;
    }

    private static long parseMatchedMethodCallTime(String matchedMethodCallTime) {
        return initWith((String matched) -> matched.replaceAll(",", "."))
                .andThen(LocalDateTime::parse)
                .andThen(localDateTime -> localDateTime.atZone(ZoneId.systemDefault()))
                .andThen(ChronoZonedDateTime::toInstant)
                .andThen(Instant::toEpochMilli)
                .apply(matchedMethodCallTime);
    }

    private static void representResult(Map<String, Map<Integer, Long>> logMap) {
        Map<Integer, Long> methodCallInfo;
        for (String methodName : logMap.keySet()) {
            methodCallInfo = logMap.get(methodName);
            System.out.printf(
                    "Method name: %s; Minimum call time: %dms; Maximum call time: %dms; Average call time: %.2fms; Number of calls: %d; ID of maximum call time: %d.%n",
                    methodName, getMethodMinCallTime(methodCallInfo), getMethodMaxCallTime(methodCallInfo),
                    getMethodAverageCallTime(methodCallInfo), getMethodCallsCount(methodCallInfo), getLongestCallMethodId(methodCallInfo));
        }
    }

    private static long getMethodMinCallTime(Map<Integer, Long> methodCallInfo) {
        return methodCallInfo.values().stream()
                .min(Long::compareTo)
                .orElseThrow(NullPointerException::new);
    }

    private static long getMethodMaxCallTime(Map<Integer, Long> methodCallInfo) {
        return methodCallInfo.values().stream().
                max(Long::compareTo).
                orElseThrow(NullPointerException::new);
    }

    private static double getMethodAverageCallTime(Map<Integer, Long> methodCallInfo) {
        return methodCallInfo.values().stream().
                map(Long::doubleValue).
                reduce(0.0, (averageCallTime, methodCallTime) -> averageCallTime += methodCallTime / methodCallInfo.values().size());
    }

    private static int getMethodCallsCount(Map<Integer, Long> methodCallInfo) {
        return methodCallInfo.size();
    }

    private static int getLongestCallMethodId(Map<Integer, Long> methodCallInfo) {
        return methodCallInfo.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .orElseThrow(NullPointerException::new)
                .getKey();
    }

    private static <T, R> Function<T, R> initWith(Function<T, R> function) {
        return function;
    }
}
