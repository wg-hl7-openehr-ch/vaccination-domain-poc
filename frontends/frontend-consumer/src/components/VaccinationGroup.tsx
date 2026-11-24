import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { DiseaseGroup, Vaccination } from '../types';
import { colors, font, radius, spacing } from '../theme';
import { formatDate } from '../utils';

interface VaccinationGroupProps {
  group: DiseaseGroup;
}

/** One card per target disease, with a dated timeline of the doses given. */
export function VaccinationGroup({ group }: VaccinationGroupProps) {
  const { t } = useTranslation();
  const count = group.entries.length;
  return (
    <View style={styles.card}>
      <View style={styles.header}>
        <View style={styles.headerText}>
          <Text accessibilityRole="header" style={styles.disease}>
            {group.disease}
          </Text>
          <Text style={styles.meta}>
            {t('group.entries', { count })} · {t('group.lastDose', { date: formatDate(group.latest, { short: true }) })}
          </Text>
        </View>
      </View>

      <View style={styles.timeline}>
        {group.entries.map((entry, index) => (
          <Entry key={entry.id} entry={entry} isLast={index === group.entries.length - 1} />
        ))}
      </View>
    </View>
  );
}

function Entry({ entry, isLast }: { entry: Vaccination; isLast: boolean }) {
  const { t } = useTranslation();
  const doseLabel = entry.doseNumber === 'Booster'
    ? t('group.booster')
    : (entry.doseNumber && entry.seriesDoses)
      ? t('group.doseOfSeries', { number: entry.doseNumber, total: entry.seriesDoses })
      : entry.dose;

  const labelParams = {
    vaccine: entry.vaccine,
    dose: doseLabel,
    date: formatDate(entry.date),
    manufacturer: entry.manufacturer,
    batch: entry.batch,
    reason: entry.vaccinationReason?.display,
  };
  const label = entry.vaccinationReason
    ? t('group.entryLabelWithReason', labelParams)
    : t('group.entryLabel', labelParams);

  return (
    <View style={styles.entryRow} accessible accessibilityLabel={label}>
      <View style={styles.rail}>
        <View style={styles.dot} />
        {!isLast && <View style={styles.line} />}
      </View>
      <View style={styles.entryBody}>
        <View style={styles.entryHead}>
          <Text style={styles.vaccine}>{entry.vaccine}</Text>
          <View style={styles.dosePill}>
            <Text style={styles.dosePillText}>{doseLabel}</Text>
          </View>
        </View>
        <Text style={styles.entryDate}>{formatDate(entry.date)}</Text>
        <Text style={styles.entrySub}>
          {entry.manufacturer} · {t('group.batch', { batch: entry.batch })}
        </Text>
        <Text style={styles.entrySub}>
          {entry.route} · {entry.site}
        </Text>
        {entry.vaccinationReason ? (
          <Text style={styles.reasonText}>
            {t('group.reason', { reason: entry.vaccinationReason.display })}
          </Text>
        ) : null}
      </View>
    </View>
  );
}


const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.lg,
    marginBottom: spacing.md,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  headerText: {
    flex: 1,
  },
  disease: {
    fontSize: font.title,
    fontWeight: '700',
    color: colors.text,
  },
  meta: {
    marginTop: 2,
    fontSize: font.label,
    color: colors.textMuted,
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.successSoft,
    borderRadius: radius.pill,
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md,
  },
  badgeText: {
    fontSize: font.caption,
    fontWeight: '700',
    color: colors.success,
  },
  timeline: {
    gap: spacing.lg,
  },
  entryRow: {
    flexDirection: 'row',
    gap: spacing.md,
  },
  rail: {
    alignItems: 'center',
    width: 12,
  },
  dot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: colors.primary,
    marginTop: 4,
  },
  line: {
    flex: 1,
    width: 2,
    backgroundColor: colors.border,
    marginTop: 4,
  },
  entryBody: {
    flex: 1,
    paddingBottom: spacing.xs,
  },
  entryHead: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  vaccine: {
    fontSize: font.body,
    fontWeight: '600',
    color: colors.text,
  },
  dosePill: {
    backgroundColor: colors.primarySoft,
    borderRadius: radius.sm,
    paddingVertical: 2,
    paddingHorizontal: spacing.sm,
  },
  dosePillText: {
    fontSize: font.caption,
    fontWeight: '600',
    color: colors.primary,
  },
  entryDate: {
    marginTop: spacing.xs,
    fontSize: font.label,
    fontWeight: '600',
    color: colors.text,
  },
  entrySub: {
    marginTop: 2,
    fontSize: font.label,
    color: colors.textMuted,
  },
  reasonText: {
    fontStyle: 'italic',
    color: colors.primary,
    fontWeight: '500',
    marginTop: 4,
    fontSize: font.label,
  },
});
