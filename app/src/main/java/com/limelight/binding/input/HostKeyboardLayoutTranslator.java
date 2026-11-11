package com.limelight.binding.input;

import java.util.Locale;

public class HostKeyboardLayoutTranslator {

    public static class TranslationResult {
        public short vkCode;
        public boolean requiresShift;
        public boolean sendAsUtf8;

        public TranslationResult(short vkCode, boolean requiresShift) {
            this.vkCode = vkCode;
            this.requiresShift = requiresShift;
            this.sendAsUtf8 = false;
        }

        public static TranslationResult utf8() {
            TranslationResult res = new TranslationResult((short) 0, false);
            res.sendAsUtf8 = true;
            return res;
        }
    }

    public static String resolveLayout(String layout) {
        if (layout == null || layout.equals("auto")) {
            String lang = Locale.getDefault().getLanguage();
            if ("tr".equalsIgnoreCase(lang)) {
                return "tr_TR";
            } else if ("de".equalsIgnoreCase(lang)) {
                return "de_DE";
            } else if ("fr".equalsIgnoreCase(lang)) {
                return "fr_FR";
            } else if ("es".equalsIgnoreCase(lang)) {
                return "es_ES";
            } else if ("it".equalsIgnoreCase(lang)) {
                return "it_IT";
            }
            return "auto";
        }
        return layout;
    }

    public static TranslationResult translate(String layout, int unicodeChar) {
        String effectiveLayout = resolveLayout(layout);
        if (effectiveLayout.equals("auto")) {
            return null;
        }

        short vk = 0;
        boolean shift = false;

        if (effectiveLayout.equals("tr_TR")) {
            switch (unicodeChar) {
                // Turkish Letters
                case 'ş': vk = 0xBA; break; // VK_OEM_1
                case 'Ş': vk = 0xBA; shift = true; break;
                case 'i': vk = 0xDE; break; // VK_OEM_7
                case 'İ': vk = 0xDE; shift = true; break;
                case 'ı': vk = 0x49; break; // VK_I
                case 'I': vk = 0x49; shift = true; break;
                case 'ğ': vk = 0xDB; break; // VK_OEM_4
                case 'Ğ': vk = 0xDB; shift = true; break;
                case 'ü': vk = 0xDD; break; // VK_OEM_6
                case 'Ü': vk = 0xDD; shift = true; break;
                case 'ç': vk = 0xBE; break; // VK_OEM_PERIOD (US '.' -> Scancode 0x34 -> TR-Q 'ç')
                case 'Ç': vk = 0xBE; shift = true; break;
                case 'ö': vk = 0xBC; break; // VK_OEM_COMMA (US ',' -> Scancode 0x33 -> TR-Q 'ö')
                case 'Ö': vk = 0xBC; shift = true; break;

                // Turkish Quotes & Number-Row Shift Symbols
                case '\'':
                case '’':
                case '‘':
                    vk = 0x32; shift = true; break; // Shift + 2 -> ''' on TR-Q
                case '"':
                case '“':
                case '”':
                    vk = 0xC0; shift = false; break; // VK_OEM_3 unshifted -> '"' on TR-Q
                case 'é':
                    vk = 0xC0; shift = true; break; // VK_OEM_3 shifted -> 'é' on TR-Q
                case '!':
                    vk = 0x31; shift = true; break; // Shift + 1
                case '^':
                    vk = 0x33; shift = true; break; // Shift + 3
                case '+':
                    vk = 0x34; shift = true; break; // Shift + 4 -> '+' on TR-Q
                case '%':
                    vk = 0x35; shift = true; break; // Shift + 5
                case '&':
                    vk = 0x36; shift = true; break; // Shift + 6 -> '&' on TR-Q
                case '/':
                    vk = 0x37; shift = true; break; // Shift + 7 -> '/' on TR-Q
                case '(':
                    vk = 0x38; shift = true; break; // Shift + 8 -> '(' on TR-Q
                case ')':
                    vk = 0x39; shift = true; break; // Shift + 9 -> ')' on TR-Q
                case '=':
                    vk = 0x30; shift = true; break; // Shift + 0 -> '=' on TR-Q

                // Turkish Punctuation
                case '.': vk = 0xBF; break; // VK_OEM_2 (US '/' -> Scancode 0x35 -> TR-Q '.')
                case ':': vk = 0xBF; shift = true; break;
                case ',': vk = 0xDC; break; // VK_OEM_5 (US '\' -> Scancode 0x2B -> TR-Q ',')
                case ';': vk = 0xDC; shift = true; break;
                case '*': vk = 0xBD; break; // VK_OEM_MINUS
                case '?': vk = 0xBD; shift = true; break;
                case '-': vk = 0xBB; break; // VK_OEM_PLUS
                case '_': vk = 0xBB; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;

                // AltGr symbols on TR-Q: send directly as UTF-8 Unicode so they don't trigger
                // Turkish letters ('ğ', 'ü', ',', ';', '"') via US physical keycodes
                case '@':
                case '#':
                case '$':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                case '|':
                case '`':
                case '~':
                    return TranslationResult.utf8();
            }
        } else if (effectiveLayout.equals("de_DE")) {
            switch (unicodeChar) {
                case 'z': vk = 0x59; break; // VK_Y
                case 'Z': vk = 0x59; shift = true; break;
                case 'y': vk = 0x5A; break; // VK_Z
                case 'Y': vk = 0x5A; shift = true; break;
                case 'ä': vk = 0xDE; break; // VK_OEM_7
                case 'Ä': vk = 0xDE; shift = true; break;
                case 'ö': vk = 0xBA; break; // VK_OEM_1
                case 'Ö': vk = 0xBA; shift = true; break;
                case 'ü': vk = 0xDB; break; // VK_OEM_4
                case 'Ü': vk = 0xDB; shift = true; break;
                case 'ß': vk = 0xBD; break; // VK_OEM_MINUS
                case '?': vk = 0xBD; shift = true; break;
                case '\'':
                case '’':
                case '‘':
                    vk = 0xDC; shift = true; break; // Shift + # (VK_OEM_5) -> ''' on DE-QWERTZ
                case '#':
                    vk = 0xDC; shift = false; break;
                case '"':
                case '“':
                case '”':
                    vk = 0x32; shift = true; break; // Shift + 2 -> '"' on DE-QWERTZ
                case '&': vk = 0x36; shift = true; break;
                case '/': vk = 0x37; shift = true; break;
                case '(': vk = 0x38; shift = true; break;
                case ')': vk = 0x39; shift = true; break;
                case '=': vk = 0x30; shift = true; break;
                case '-': vk = 0xBF; break; // VK_OEM_2
                case '_': vk = 0xBF; shift = true; break;
                case '+': vk = 0xDD; break; // US ']' -> Scancode 0x1B -> DE '+'
                case '*': vk = 0xDD; shift = true; break;
                case ',': vk = 0xBC; break;
                case ';': vk = 0xBC; shift = true; break;
                case '.': vk = 0xBE; break;
                case ':': vk = 0xBE; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
                case '@':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                case '|':
                case '~':
                    return TranslationResult.utf8();
            }
        } else if (effectiveLayout.equals("fr_FR")) {
            switch (unicodeChar) {
                case 'a': vk = 0x51; break; // VK_Q
                case 'A': vk = 0x51; shift = true; break;
                case 'q': vk = 0x41; break; // VK_A
                case 'Q': vk = 0x41; shift = true; break;
                case 'w': vk = 0x5A; break; // VK_Z
                case 'W': vk = 0x5A; shift = true; break;
                case 'z': vk = 0x57; break; // VK_W
                case 'Z': vk = 0x57; shift = true; break;
                case 'm': vk = 0xBA; break; // VK_OEM_1
                case 'M': vk = 0xBA; shift = true; break;
                case 'ù': vk = 0xDE; break; // VK_OEM_7
                case '%': vk = 0xDE; shift = true; break;
                case '&': vk = 0x31; break; // 1 unshifted
                case 'é': vk = 0x32; break; // 2 unshifted
                case '"':
                case '“':
                case '”':
                    vk = 0x33; shift = false; break; // 3 unshifted -> '"' on FR-AZERTY
                case '\'':
                case '’':
                case '‘':
                    vk = 0x34; shift = false; break; // 4 unshifted -> ''' on FR-AZERTY
                case '(': vk = 0x35; break; // 5 unshifted
                case '-': vk = 0x36; break; // 6 unshifted
                case 'è': vk = 0x37; break; // 7 unshifted
                case '_': vk = 0x38; break; // 8 unshifted
                case 'ç': vk = 0x39; break; // 9 unshifted
                case 'à': vk = 0x30; break; // 0 unshifted
                case ')': vk = 0xBD; break; // US '-' -> FR ')'
                case '=': vk = 0xBB; break;
                case '+': vk = 0xBB; shift = true; break;
                case '1': vk = 0x31; shift = true; break;
                case '2': vk = 0x32; shift = true; break;
                case '3': vk = 0x33; shift = true; break;
                case '4': vk = 0x34; shift = true; break;
                case '5': vk = 0x35; shift = true; break;
                case '6': vk = 0x36; shift = true; break;
                case '7': vk = 0x37; shift = true; break;
                case '8': vk = 0x38; shift = true; break;
                case '9': vk = 0x39; shift = true; break;
                case '0': vk = 0x30; shift = true; break;
                case ',': vk = 0xBC; break;
                case '?': vk = 0xBC; shift = true; break;
                case ';': vk = 0xBE; break;
                case '.': vk = 0xBE; shift = true; break;
                case ':': vk = 0xBF; break;
                case '/': vk = 0xBF; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
                case '@':
                case '#':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                case '|':
                case '~':
                    return TranslationResult.utf8();
            }
        } else if (effectiveLayout.equals("es_ES")) {
            switch (unicodeChar) {
                case 'ñ': vk = 0xBA; break; // VK_OEM_1
                case 'Ñ': vk = 0xBA; shift = true; break;
                case 'ç': vk = 0xDC; break; // VK_OEM_5
                case 'Ç': vk = 0xDC; shift = true; break;
                case '¡': vk = 0xBB; break; // VK_OEM_PLUS
                case '¿': vk = 0xBB; shift = true; break;
                case '\'':
                case '’':
                case '‘':
                    vk = 0xBD; shift = false; break; // VK_OEM_MINUS unshifted -> ''' on ES
                case '?':
                    vk = 0xBD; shift = true; break;
                case '"':
                case '“':
                case '”':
                    vk = 0x32; shift = true; break; // Shift + 2 -> '"' on ES
                case '&': vk = 0x36; shift = true; break;
                case '/': vk = 0x37; shift = true; break;
                case '(': vk = 0x38; shift = true; break;
                case ')': vk = 0x39; shift = true; break;
                case '=': vk = 0x30; shift = true; break;
                case '+': vk = 0xDD; break; // VK_OEM_6
                case '*': vk = 0xDD; shift = true; break;
                case ',': vk = 0xBC; break;
                case ';': vk = 0xBC; shift = true; break;
                case '.': vk = 0xBE; break;
                case ':': vk = 0xBE; shift = true; break;
                case '-': vk = 0xBF; break; // VK_OEM_2
                case '_': vk = 0xBF; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
                case '@':
                case '#':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                case '|':
                case '~':
                    return TranslationResult.utf8();
            }
        } else if (effectiveLayout.equals("it_IT")) {
            switch (unicodeChar) {
                case 'ò': vk = 0xBA; break; // VK_OEM_1
                case 'ç': vk = 0xBA; shift = true; break;
                case 'à': vk = 0xDE; break; // VK_OEM_7
                case '°': vk = 0xDE; shift = true; break;
                case 'ù': vk = 0xDC; break; // VK_OEM_5
                case '§': vk = 0xDC; shift = true; break;
                case 'è': vk = 0xDB; break; // VK_OEM_4
                case 'é': vk = 0xDB; shift = true; break;
                case 'ì': vk = 0xBB; break; // VK_OEM_PLUS
                case '^': vk = 0xBB; shift = true; break;
                case '\'':
                case '’':
                case '‘':
                    vk = 0xBD; shift = false; break; // VK_OEM_MINUS unshifted -> ''' on IT
                case '?':
                    vk = 0xBD; shift = true; break;
                case '"':
                case '“':
                case '”':
                    vk = 0x32; shift = true; break; // Shift + 2 -> '"' on IT
                case '&': vk = 0x36; shift = true; break;
                case '/': vk = 0x37; shift = true; break;
                case '(': vk = 0x38; shift = true; break;
                case ')': vk = 0x39; shift = true; break;
                case '=': vk = 0x30; shift = true; break;
                case '+': vk = 0xDD; break;
                case '*': vk = 0xDD; shift = true; break;
                case ',': vk = 0xBC; break;
                case ';': vk = 0xBC; shift = true; break;
                case '.': vk = 0xBE; break;
                case ':': vk = 0xBE; shift = true; break;
                case '-': vk = 0xBF; break;
                case '_': vk = 0xBF; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
                case '@':
                case '#':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                case '|':
                case '~':
                    return TranslationResult.utf8();
            }
        }

        if (vk != 0) {
            return new TranslationResult(vk, shift);
        }

        return null;
    }
}
