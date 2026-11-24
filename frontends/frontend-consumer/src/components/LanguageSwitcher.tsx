import { useEffect, useRef, useState } from 'react';
import { Modal, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';

import { LANGUAGE_NAMES, SUPPORTED_LANGUAGES, chooseLanguage, currentLanguage, type Language } from '../i18n';
import { colors, font, radius, spacing } from '../theme';

type Anchor = { top: number; right: number };

/**
 * Language picker for the app bar. Compact: code-only button that opens a bottom sheet.
 * Otherwise: button with the language name and a dropdown anchored below it.
 */
export function LanguageSwitcher({ compact }: { compact: boolean }) {
  const { t } = useTranslation();
  const insets = useSafeAreaInsets();
  const { width: windowWidth } = useWindowDimensions();
  const language = currentLanguage();
  const [anchor, setAnchor] = useState<Anchor | null>(null);
  const triggerRef = useRef<View>(null);
  const optionRefs = useRef<(View | null)[]>([]);
  const open = anchor !== null;

  const openMenu = () => {
    triggerRef.current?.measureInWindow((x, y, width, height) => {
      setAnchor({ top: y + height + spacing.xs, right: windowWidth - (x + width) });
    });
  };

  const close = () => setAnchor(null);

  const choose = (lng: Language) => {
    chooseLanguage(lng);
    close();
  };

  const focusOption = (index: number) => {
    const count = SUPPORTED_LANGUAGES.length;
    const option = optionRefs.current[(index + count) % count] as unknown as HTMLElement | null;
    option?.focus();
  };

  useEffect(() => {
    if (!open || Platform.OS !== 'web') return;
    const frame = requestAnimationFrame(() => focusOption(SUPPORTED_LANGUAGES.indexOf(language)));
    return () => cancelAnimationFrame(frame);
  }, [open]);

  const onKeyDown = (e: { key: string; preventDefault: () => void }) => {
    const index = optionRefs.current.findIndex((el) => (el as unknown) === document.activeElement);
    if (e.key === 'ArrowDown') { e.preventDefault(); focusOption(index + 1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); focusOption(index - 1); }
    else if (e.key === 'Home') { e.preventDefault(); focusOption(0); }
    else if (e.key === 'End') { e.preventDefault(); focusOption(SUPPORTED_LANGUAGES.length - 1); }
  };
  const webKeyboardProps = Platform.OS === 'web' ? ({ onKeyDown } as object) : {};

  const options = (
    <View accessibilityRole="radiogroup" accessibilityLabel={t('language.title')} {...webKeyboardProps}>
      {SUPPORTED_LANGUAGES.map((lng, i) => {
        const selected = lng === language;
        return (
          <Pressable
            key={lng}
            ref={(el) => { optionRefs.current[i] = el; }}
            onPress={() => choose(lng)}
            accessibilityRole="radio"
            aria-checked={selected}
            accessibilityLabel={LANGUAGE_NAMES[lng]}
            accessibilityLanguage={lng}
            style={({ pressed }) => [
              compact ? styles.sheetOption : styles.menuOption,
              selected && styles.optionSelected,
              pressed && styles.pressed,
            ]}>
            <Text style={styles.optionCode}>{lng.toUpperCase()}</Text>
            <Text style={[compact ? styles.sheetOptionName : styles.menuOptionName, selected && styles.optionNameSelected]}>
              {LANGUAGE_NAMES[lng]}
            </Text>
            {selected && <Text style={styles.optionCheck}>✓</Text>}
          </Pressable>
        );
      })}
    </View>
  );

  return (
    <>
      <Pressable
        ref={triggerRef}
        onPress={openMenu}
        accessibilityRole="button"
        accessibilityLabel={t('language.button', { language: LANGUAGE_NAMES[language] })}
        aria-expanded={open}
        hitSlop={spacing.xs}
        style={({ pressed }) => [styles.trigger, compact && styles.triggerCompact, (pressed || open) && styles.triggerActive]}>
        <Text style={styles.triggerText}>{compact ? language.toUpperCase() : LANGUAGE_NAMES[language]}</Text>
        <View style={[styles.chevron, open && styles.chevronOpen]} />
      </Pressable>

      <Modal visible={open} transparent animationType={compact ? 'fade' : 'none'} onRequestClose={close}>
        <View style={[styles.overlay, compact && styles.overlayDimmed]}>
          <Pressable
            style={StyleSheet.absoluteFill}
            onPress={close}
            accessibilityRole="button"
            accessibilityLabel={t('common.close')}
          />
          {compact ? (
            <View style={[styles.sheet, { paddingBottom: insets.bottom + spacing.lg }]} accessibilityViewIsModal>
              <View style={styles.handle} />
              <Text accessibilityRole="header" style={styles.sheetTitle}>{t('language.title')}</Text>
              {options}
            </View>
          ) : (
            anchor && (
              <View style={[styles.menu, { top: anchor.top, right: anchor.right }]} accessibilityViewIsModal>
                {options}
              </View>
            )
          )}
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  trigger: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
    minWidth: 44,
    minHeight: 44,
    paddingHorizontal: spacing.md,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.bg,
  },
  triggerCompact: {
    paddingHorizontal: spacing.sm,
  },
  triggerActive: {
    borderColor: colors.borderStrong,
  },
  triggerText: {
    fontSize: font.label,
    fontWeight: '600',
    color: colors.text,
  },
  chevron: {
    width: 8,
    height: 8,
    marginLeft: 2,
    marginTop: -4,
    borderRightWidth: 2,
    borderBottomWidth: 2,
    borderColor: colors.textMuted,
    transform: [{ rotate: '45deg' }],
  },
  chevronOpen: {
    marginTop: 4,
    transform: [{ rotate: '225deg' }],
  },
  pressed: {
    opacity: 0.7,
  },
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  overlayDimmed: {
    backgroundColor: 'rgba(11, 31, 51, 0.35)',
  },
  sheet: {
    width: '100%',
    maxWidth: 560,
    alignSelf: 'center',
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
  },
  handle: {
    alignSelf: 'center',
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.borderStrong,
    marginBottom: spacing.md,
  },
  sheetTitle: {
    fontSize: font.title,
    fontWeight: '700',
    color: colors.text,
    marginBottom: spacing.sm,
  },
  sheetOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    minHeight: 52,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
  },
  sheetOptionName: {
    flex: 1,
    fontSize: font.body,
    color: colors.text,
  },
  menu: {
    position: 'absolute',
    minWidth: 200,
    padding: spacing.xs,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: colors.shadow,
    shadowOpacity: 0.12,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 8,
  },
  menuOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    minHeight: 40,
    paddingHorizontal: spacing.sm,
    borderRadius: radius.sm,
  },
  menuOptionName: {
    flex: 1,
    fontSize: font.label,
    color: colors.text,
  },
  optionSelected: {
    backgroundColor: colors.primarySoft,
  },
  optionCode: {
    width: 24,
    fontSize: font.caption,
    fontWeight: '600',
    color: colors.textFaint,
  },
  optionNameSelected: {
    fontWeight: '700',
    color: colors.primary,
  },
  optionCheck: {
    fontSize: font.label,
    fontWeight: '700',
    color: colors.primary,
  },
});
