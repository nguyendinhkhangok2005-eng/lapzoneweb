package com.lapzone.lapzoneweb.model.service;

import com.lapzone.lapzoneweb.model.dto.ChatMessageDTO;
import com.lapzone.lapzoneweb.model.entity.Product;
import com.lapzone.lapzoneweb.model.entity.ProductDetail;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Deterministic constraints: the AI can rank candidates but cannot relax these. */
final class AiProductRequirements {
    private final Map<String, List<String>> terms = new LinkedHashMap<>();
    private final Map<String, Double> capacities = new HashMap<>();
    private Double minPrice, maxPrice, maxWeight;

    static AiProductRequirements from(String question, List<ChatMessageDTO> history) {
        AiProductRequirements result = new AiProductRequirements();
        if (history != null) history.stream().filter(Objects::nonNull)
            .filter(m -> "user".equals(m.role())).forEach(m -> result.update(m.text()));
        result.update(question);
        return result;
    }

    static String normalize(String text) {
        return Normalizer.normalize(Objects.toString(text, "").toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replace('đ', 'd');
    }

    private void update(String text) {
        String q = normalize(text);
        if (q.matches("(?s).*(?:tim moi|nhu cau moi|bat dau lai).*")) {
            terms.clear(); capacities.clear(); minPrice = null; maxPrice = null; maxWeight = null;
        }
        // A range's first amount may omit its unit: "từ 15 đến 20 triệu".
        Matcher range = Pattern.compile("(?:tu|between)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:trieu|tr|m)?\\s*(?:den|toi|and|-)\\s*(\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|m)\\b").matcher(q);
        if (range.find()) {
            minPrice = number(range.group(1)) * 1_000_000;
            maxPrice = number(range.group(2)) * 1_000_000;
        } else {
            Matcher money = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|m|vnd|dong|₫|d\\b|đ)(?![a-z])").matcher(q);
            while (money.find()) {
                String unit = money.group(2);
                double amount = List.of("trieu", "tr", "m").contains(unit)
                    ? number(money.group(1)) * 1_000_000 : Double.parseDouble(money.group(1).replaceAll("[.,]", ""));
                String prefix = q.substring(Math.max(0, money.start() - 25), money.start());
                if (prefix.matches("(?s).*(?:tren|tu|toi thieu|it nhat|minimum|over)\\s*$")) {
                    minPrice = prefix.matches("(?s).*tren\\s*$") ? Math.nextUp(amount) : amount;
                    maxPrice = null;
                } else {
                    maxPrice = prefix.matches("(?s).*(?:duoi|less than)\\s*$") ? Math.nextDown(amount) : amount;
                    minPrice = null;
                }
            }
            // Also accept unadorned VND amounts after an explicit budget/price phrase.
            Matcher raw = Pattern.compile("(?:ngan sach|gia|budget|duoi|khong qua)\\s*(\\d{1,3}(?:[.,]\\d{3}){2}|\\d{7,9})(?!\\d)").matcher(q);
            if (raw.find()) {
                double amount = Double.parseDouble(raw.group(1).replaceAll("[.,]", ""));
                maxPrice = raw.group().startsWith("duoi") ? Math.nextDown(amount) : amount;
                minPrice = null;
            }
        }
        collect(q, "cpu", "\\b(?:i[3579](?:[ -]+\\d{4,5}[a-z]*)?|ryzen\\s*[3579](?:[ -]+\\d{4}[a-z]*)?|(?:core\\s+)?ultra\\s+[579](?:\\s+\\d{3}[a-z]*)?|m[1-9](?:\\s+(?:pro|max|ultra))?|celeron|pentium|xeon)\\b");
        if (!terms.containsKey("cpu") || !q.matches("(?s).*\\b(?:i[3579]|ryzen|ultra|m[1-9])\\b.*")) {
            if (q.matches("(?s).*\\b(?:cpu|chip)\\s+(?:intel|amd)\\b.*")) collect(q, "cpu", "\\b(?:intel|amd)\\b");
        }
        collect(q, "gpu", "\\b(?:(?:rtx|gtx|rx)\\s*\\d{3,4}(?:\\s*(?:ti|super))?|iris\\s+xe|radeon(?:\\s+\\d{3,4}m)?|intel\\s+arc)\\b");
        if (q.matches("(?s).*\\b(?:rtx|gtx)\\b.*") && !q.matches("(?s).*\\b(?:rtx|gtx)\\s*\\d.*")) collect(q, "gpu", "\\b(?:rtx|gtx)\\b");
        collect(q, "brand", "\\b(?:asus|acer|lenovo|dell|hp|msi|gigabyte|apple|macbook)\\b");
        capacity(q, "ram", "ram");
        capacity(q, "storage", "ssd|hdd|o cung|storage");
        collect(q, "storageType", "\\b(?:ssd|hdd|nvme)\\b");
        collect(q, "ramType", "\\b(?:lpddr[345]x?|ddr[345])\\b");
        collect(q, "screen", "\\b(?:oled|ips|mini led|touchscreen|cam ung)\\b");
        collect(q, "resolution", "\\b(?:fhd\\+?|qhd\\+?|uhd|[234](?:\\.[0-9])?k)\\b");
        collect(q, "screenSize", "\\b\\d{2}(?:[.,]\\d)?\\s*(?:inch|inches|\")");
        collect(q, "refresh", "\\b\\d{2,3}\\s*hz\\b");
        collect(q, "ports", "\\b(?:thunderbolt\\s*[345]?|hdmi(?:\\s*2\\.[01])?|usb-c|usb-a)\\b");
        collect(q, "wireless", "\\b(?:wi-?fi\\s*[567]e?|bluetooth\\s*[45](?:\\.[0-9])?)\\b");
        collect(q, "audio", "\\b(?:dolby atmos|harman kardon|dts:x)\\b");
        if (q.contains("mau ")) collect(q, "color", "\\b(?:den|bac|xam|trang|xanh|hong|vang)\\b");
        if (q.contains("bao hanh")) collect(q, "warranty", "\\b\\d+\\s*thang\\b");
        collect(q, "battery", "\\b\\d+\\s*wh\\b");
        if (q.contains("may cu") || q.contains("da qua su dung")) terms.put("condition", List.of("used", "cu", "da qua su dung"));
        if (q.contains("may moi") || q.contains("moi 100%")) terms.put("condition", List.of("new", "moi"));
        Matcher weight = Pattern.compile("(?:duoi|nhe hon|toi da|khong qua)\\s*(\\d+(?:[.,]\\d+)?)\\s*kg").matcher(q);
        if (weight.find()) maxWeight = number(weight.group(1));
        Map<String, List<String>> purposes = Map.of(
            "purposeGaming", List.of("gaming", "choi game", "game"),
            "purposeDesign", List.of("do hoa", "thiet ke", "design", "render"),
            "purposeCode", List.of("lap trinh", "coding", "programming"),
            "purposeOffice", List.of("van phong", "office", "hoc tap"));
        if (purposes.values().stream().flatMap(aliases -> aliases.stream()).anyMatch(q::contains)) {
            terms.keySet().removeIf(key -> key.startsWith("purpose"));
            purposes.forEach((key, aliases) -> { if (aliases.stream().anyMatch(q::contains)) terms.put(key, aliases); });
        }
    }

    private void collect(String q, String key, String regex) {
        List<String> matches = Pattern.compile(regex).matcher(q).results().map(match -> match.group()).distinct().toList();
        if (!matches.isEmpty()) terms.put(key, matches);
    }

    private void capacity(String q, String key, String labels) {
        Matcher m = Pattern.compile("(?:" + labels + ")\\s*(?:(toi thieu|it nhat|toi da|khong qua|dung)\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(gb|tb)\\b|(?:(toi thieu|it nhat|toi da|khong qua|dung)\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(gb|tb)\\s*(?:" + labels + ")\\b").matcher(q);
        if (m.find()) {
            String mode = m.group(2) == null ? m.group(4) : m.group(1);
            double amount = number(m.group(2) == null ? m.group(5) : m.group(2));
            String unit = m.group(2) == null ? m.group(6) : m.group(3);
            capacities.put(key, amount * ("tb".equals(unit) ? 1024 : 1));
            terms.put(key + "Mode", List.of(mode == null ? "minimum" : mode));
        }
    }

    boolean matches(Product p) {
        if (p.getId() == null || p.getId() <= 0 || p.getName() == null || p.getName().isBlank()
                || p.getPrice() == null || !Double.isFinite(p.getPrice()) || p.getPrice() <= 0
                || p.getStock() == null || p.getStock() <= 0 || !matchesPrice(PromotionService.getDiscountedPrice(p))) return false;
        if (!matchesRequiredSpecifications(p)) return false;
        ProductDetail detail = p.getProductDetail();
        if (detail == null && terms.keySet().stream().anyMatch(key -> key.startsWith("purpose"))) return false;
        String purpose = p.getName() + " " + (p.getCategory() == null ? "" : p.getCategory().getName()) + " "
            + (detail == null ? "" : detail.getMoreInfo());
        return terms.keySet().stream().filter(key -> key.startsWith("purpose")).noneMatch(key -> !has(key, purpose));
    }

    boolean hasBudgetConstraint() { return minPrice != null || maxPrice != null; }
    boolean hasSpecificationConstraints() {
        return !capacities.isEmpty() || maxWeight != null || terms.keySet().stream()
            .anyMatch(key -> !key.startsWith("purpose") && !key.endsWith("Mode"));
    }
    boolean hasUnparsedSpecificationRequest(String question) {
        String q = normalize(question);
        return (q.matches("(?s).*\\b(?:cpu|chip)\\b.*") && !terms.containsKey("cpu"))
            || (q.matches("(?s).*\\b(?:gpu|card)\\b.*") && !terms.containsKey("gpu"))
            || (q.matches("(?s).*\\bram\\b.*") && !capacities.containsKey("ram") && !terms.containsKey("ramType"))
            || (q.matches("(?s).*(?:o cung|storage).*" ) && !capacities.containsKey("storage") && !terms.containsKey("storageType"));
    }

    boolean hasMissingSpecificationData(Product p) {
        Map<String, String> data = new HashMap<>();
        ProductDetail d = p.getProductDetail();
        data.put("brand", p.getName());
        if (d != null) {
            data.put("cpu", d.getCpu()); data.put("gpu", d.getGpu()); data.put("ram", d.getRam()); data.put("ramType", d.getRam());
            data.put("storage", d.getStorage()); data.put("storageType", d.getStorage()); data.put("screen", d.getScreen());
            data.put("resolution", d.getScreen()); data.put("screenSize", d.getScreen()); data.put("refresh", d.getScreen());
            data.put("ports", d.getPorts()); data.put("wireless", d.getWireless()); data.put("audio", d.getAudio());
            data.put("color", d.getColor()); data.put("warranty", d.getWarranty()); data.put("battery", d.getBattery());
            data.put("condition", d.getConditionStatus()); data.put("weight", d.getWeight());
        }
        Set<String> fields = new HashSet<>(capacities.keySet());
        terms.keySet().stream().filter(key -> !key.startsWith("purpose") && !key.endsWith("Mode")).forEach(fields::add);
        if (maxWeight != null) fields.add("weight");
        return fields.stream().anyMatch(key -> data.get(key) == null || data.get(key).isBlank());
    }

    boolean matchesRequiredSpecifications(Product p) {
        ProductDetail d = p.getProductDetail();
        if (!has("brand", p.getName())) return false;
        if (d == null) return terms.keySet().stream().filter(key -> !key.startsWith("purpose"))
            .allMatch("brand"::equals) && capacities.isEmpty() && maxWeight == null;
        if (!has("cpu", d.getCpu()) || !has("gpu", d.getGpu()) || !has("ramType", d.getRam())
                || !has("storageType", d.getStorage()) || !has("screen", d.getScreen())
                || !has("screenSize", d.getScreen()) || !has("refresh", d.getScreen())
                || !has("resolution", d.getScreen()) || !has("audio", d.getAudio())
                || !has("ports", d.getPorts()) || !has("wireless", d.getWireless())
                || !has("color", d.getColor()) || !has("warranty", d.getWarranty())
                || !has("battery", d.getBattery()) || !has("condition", d.getConditionStatus())) return false;
        if (!capacityMatches("ram", d.getRam()) || !capacityMatches("storage", d.getStorage())) return false;
        if (maxWeight != null) {
            Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kg").matcher(normalize(d.getWeight()));
            if (!m.find() || number(m.group(1)) > maxWeight) return false;
        }
        return true;
    }

    boolean matchesPrice(Double price) {
        return price != null && Double.isFinite(price) && price > 0
            && (minPrice == null || price >= minPrice) && (maxPrice == null || price <= maxPrice);
    }

    private boolean has(String key, String value) {
        if (!terms.containsKey(key)) return true;
        String text = compact(value);
        if ("screenSize".equals(key)) return terms.get(key).stream().anyMatch(term -> text.contains(
            compact(term).replaceAll("inches|inch|\"", "")));
        if (List.of("ports", "wireless", "storageType", "ramType", "screen", "refresh").contains(key))
            return terms.get(key).stream().allMatch(term -> text.contains(compact(term)));
        return terms.get(key).stream().anyMatch(term -> text.contains(compact(term)));
    }

    private static String compact(String text) { return normalize(text).replace(',', '.').replaceAll("[\\s\\-]", ""); }

    private boolean capacityMatches(String key, String value) {
        if (!capacities.containsKey(key)) return true;
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(gb|tb)\\b").matcher(normalize(value));
        // Only installed capacity counts, never later text advertising an upgrade limit.
        if (!m.find()) return false;
        double actual = number(m.group(1)) * ("tb".equals(m.group(2)) ? 1024 : 1);
        double required = capacities.get(key);
        String mode = terms.get(key + "Mode").getFirst();
        return switch (mode) {
            case "toi da", "khong qua" -> actual <= required;
            case "dung" -> actual == required;
            default -> actual >= required;
        };
    }

    private static double number(String text) { return Double.parseDouble(text.replace(',', '.')); }
}
