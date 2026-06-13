package com.limelight.binding.input;

public class HostKeyboardLayoutTranslator {

    public static class TranslationResult {
        public short vkCode;
        public boolean requiresShift;

        public TranslationResult(short vkCode, boolean requiresShift) {
            this.vkCode = vkCode;
            this.requiresShift = requiresShift;
        }
    }

    public static TranslationResult translate(String layout, int unicodeChar) {
        if (layout == null || layout.equals("auto")) {
            return null;
        }

        short vk = 0;
        boolean shift = false;

        if (layout.equals("tr_TR")) {
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
                case 'ç': vk = 0xDC; break; // VK_OEM_5
                case 'Ç': vk = 0xDC; shift = true; break;
                case 'ö': vk = 0xBC; break; // VK_OEM_COMMA
                case 'Ö': vk = 0xBC; shift = true; break;
                
                // Turkish Punctuation
                case '.': vk = 0xBF; break; // VK_OEM_2
                case ':': vk = 0xBF; shift = true; break;
                case ',': vk = 0xC0; break; // VK_OEM_3
                case ';': vk = 0xC0; shift = true; break;
                case '*': vk = 0xBD; break; // VK_OEM_MINUS
                case '?': vk = 0xBD; shift = true; break;
                case '-': vk = 0xBB; break; // VK_OEM_PLUS
                case '_': vk = 0xBB; shift = true; break;
                case '"': vk = 0xDF; break; // VK_OEM_8
                case 'é': vk = 0xDF; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
            }
        } else if (layout.equals("de_DE")) {
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
                case '-': vk = 0xBF; break; // VK_OEM_2
                case '_': vk = 0xBF; shift = true; break;
                case '+': vk = 0xBB; break; // VK_OEM_PLUS
                case '*': vk = 0xBB; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
            }
        } else if (layout.equals("fr_FR")) {
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
                case 'ç': vk = 0x39; break; // 9
                case 'é': vk = 0x32; break; // 2
                case 'è': vk = 0x37; break; // 7
                case 'à': vk = 0x30; break; // 0
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
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
            }
        } else if (layout.equals("es_ES")) {
            switch (unicodeChar) {
                case 'ñ': vk = 0xBA; break; // VK_OEM_1
                case 'Ñ': vk = 0xBA; shift = true; break;
                case 'ç': vk = 0xDC; break; // VK_OEM_5
                case 'Ç': vk = 0xDC; shift = true; break;
                case '¡': vk = 0xBB; break; // VK_OEM_PLUS
                case '¿': vk = 0xBB; shift = true; break;
                case '´': vk = 0xBA; break; // VK_OEM_1
                case 'º': vk = 0xDC; break; // VK_OEM_5
                case 'ª': vk = 0xDC; shift = true; break;
                case '+': vk = 0xDD; break; // VK_OEM_6
                case '*': vk = 0xDD; shift = true; break;
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
                case '-': vk = 0xBF; break; // VK_OEM_2
                case '_': vk = 0xBF; shift = true; break;
                case '\'': vk = 0xBD; break; // VK_OEM_MINUS
                case '?': vk = 0xBD; shift = true; break;
            }
        } else if (layout.equals("it_IT")) {
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
                case '<': vk = 0xE2; break; // VK_OEM_102
                case '>': vk = 0xE2; shift = true; break;
            }
        }

        if (vk != 0) {
            return new TranslationResult(vk, shift);
        }

        return null;
    }
}
