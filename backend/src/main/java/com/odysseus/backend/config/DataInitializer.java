package com.odysseus.backend.config;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Bootstraps the minimum required system configuration:
 * 1. Admin and Faculty login accounts (credentials only).
 * 2. Default NEP 2020 credit rule.
 * 3. Standard college timeslots (Monâ€“Fri, P1â€“P8 + BREAK + LUNCH).
 *
 * Everything else (rooms, batches, subjects, faculty, offerings, calendars)
 * is intentionally left empty â€” the administrator enters real institution data.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final NepCreditRuleRepository nepCreditRuleRepository;
    private final CollegeTimeslotRepository timeslotRepository;
    private final CalendarHolidayRepository holidayRepository;
    private final AcademicCalendarRepository calendarRepository;
    private final BatchRepository batchRepository;
    private final PasswordEncoder passwordEncoder;
    private final javax.sql.DataSource dataSource;

    @jakarta.annotation.PostConstruct
    public void migrateSchema() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE faculty ADD COLUMN IF NOT EXISTS hod BOOLEAN DEFAULT FALSE");
            stmt.execute("UPDATE subjects SET department = 'Computer Science Engineering' WHERE department = '[object Object]'");
            stmt.execute("UPDATE batches SET department = 'Computer Science Engineering', batch_name = 'CSE-A' WHERE section = 'A' AND department = '[object Object]'");
            stmt.execute("UPDATE batches SET department = 'Computer Science Engineering', batch_name = 'CSE-B' WHERE section = 'B' AND department = '[object Object]'");
            stmt.execute("DELETE FROM batches WHERE department = '[object Object]'");
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS department VARCHAR(255)");
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20)");
        } catch (Exception ignored) {
        }
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedDefaultNepRule();
        seedCollegeTimeslots();
        seedGovernmentHolidays();
        seedDefaultAcademicCalendar();
    }

    private void seedEssentialSections() {
        String dept = "Computer Science Engineering";
        String academicYear = "2026-2027";
        int[] semesters = {3, 5};
        char[] sections = {'A', 'B', 'C', 'D', 'E', 'F', 'G'};

        for (int sem : semesters) {
            for (char sec : sections) {
                String secStr = String.valueOf(sec);
                String batchName = "CSE-" + secStr;
                boolean exists = batchRepository.findByDepartmentAndSemesterAndSection(dept, sem, secStr).isPresent();
                if (!exists) {
                    batchRepository.save(Batch.builder()
                            .department(dept)
                            .semester(sem)
                            .section(secStr)
                            .batchName(batchName)
                            .strength(60)
                            .academicYear(academicYear)
                            .active(true)
                            .build());
                }
            }
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // 5.  Default Academic Calendar
    //     Creates a placeholder active calendar so that Data Readiness passes
    //     on a fresh install. The admin can update / replace it via Year Calendar.
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void seedDefaultAcademicCalendar() {
        if (calendarRepository.findByActiveTrue().isEmpty()) {
            calendarRepository.save(AcademicCalendar.builder()
                    .academicYear("2026-2027")
                    .semester(1)
                    .startDate(LocalDate.of(2026, 7, 1))
                    .endDate(LocalDate.of(2026, 11, 30))
                    .SaturdayRule("ALL_HOLIDAY")
                    .SundayRule("ALL_HOLIDAY")
                    .active(true)
                    .build());
        }
    }


    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // 1.  Login accounts
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void seedUsers() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@odysseus.edu")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ROLE_ADMIN")
                    .fullName("System Administrator")
                    .active(true)
                    .build());
        }

        if (!userRepository.existsByUsername("faculty")) {
            userRepository.save(User.builder()
                    .username("faculty")
                    .email("faculty@odysseus.edu")
                    .password(passwordEncoder.encode("faculty123"))
                    .role("ROLE_FACULTY")
                    .fullName("Faculty User")
                    .department("Computer Science and Engineering")
                    .active(true)
                    .build());
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // 2.  NEP 2020 credit rule
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void seedDefaultNepRule() {
        if (nepCreditRuleRepository.count() == 0) {
            nepCreditRuleRepository.save(NepCreditRule.builder()
                    .ruleName("Default NEP 2020 Credit Rule")
                    .academicYear("2026-2027")
                    .program("All Programmes")
                    .theoryHoursPerCredit(1.0)
                    .labHoursPerCredit(2.0)
                    .tutorialHoursPerCredit(1.0)
                    .active(true)
                    .build());
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // 3.  Standard college timeslots  (Mon â€“ Fri, 08:15 â€“ 15:45)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void seedCollegeTimeslots() {
        if (timeslotRepository.count() > 0) return;

        List<String> days = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");

        for (String day : days) {
            // P1 08:15 â€“ 09:05
            save(day, 1, "P1", LocalTime.of(8, 15), LocalTime.of(9, 5), false, false);
            // P2 09:05 â€“ 09:55
            save(day, 2, "P2", LocalTime.of(9, 5), LocalTime.of(9, 55), false, false);
            // BREAK 09:55 â€“ 10:10
            save(day, 0, "BREAK", LocalTime.of(9, 55), LocalTime.of(10, 10), true, false);
            // P3 10:10 â€“ 11:00
            save(day, 3, "P3", LocalTime.of(10, 10), LocalTime.of(11, 0), false, false);
            // P4 11:00 â€“ 11:50
            save(day, 4, "P4", LocalTime.of(11, 0), LocalTime.of(11, 50), false, false);
            // P5 11:50 â€“ 12:40
            save(day, 5, "P5", LocalTime.of(11, 50), LocalTime.of(12, 40), false, false);
            // LUNCH 12:40 â€“ 13:30
            save(day, 0, "LUNCH", LocalTime.of(12, 40), LocalTime.of(13, 30), false, true);
            // P6 13:30 â€“ 14:15
            save(day, 6, "P6", LocalTime.of(13, 30), LocalTime.of(14, 15), false, false);
            // P7 14:15 â€“ 15:00
            save(day, 7, "P7", LocalTime.of(14, 15), LocalTime.of(15, 0), false, false);
            // P8 15:00 â€“ 15:45
            save(day, 8, "P8", LocalTime.of(15, 0), LocalTime.of(15, 45), false, false);
        }
    }

    private void save(String day, int num, String label,
                      LocalTime start, LocalTime end,
                      boolean isBreak, boolean isLunch) {
        timeslotRepository.save(CollegeTimeslot.builder()
                .dayOfWeek(day).periodNumber(num).periodLabel(label)
                .startTime(start).endTime(end)
                .isBreak(isBreak).isLunch(isLunch).active(true)
                .build());
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // 4.  Official Government of India Gazetted Holidays (2025, 2026, 2027)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private void seedGovernmentHolidays() {
        // 2026 Central Government Holidays
        addGovtHoliday("2026-01-26", "Republic Day", "National Holiday");
        addGovtHoliday("2026-02-15", "Maha Shivratri", "Gazetted Holiday");
        addGovtHoliday("2026-03-04", "Holi", "Gazetted Holiday");
        addGovtHoliday("2026-03-21", "Id-ul-Fitr (Ramzan Id)", "Gazetted Holiday");
        addGovtHoliday("2026-03-31", "Mahavir Jayanti", "Gazetted Holiday");
        addGovtHoliday("2026-04-03", "Good Friday", "Gazetted Holiday");
        addGovtHoliday("2026-04-14", "Dr. B.R. Ambedkar Jayanti", "Gazetted Holiday");
        addGovtHoliday("2026-05-01", "May Day / Buddha Purnima", "Gazetted Holiday");
        addGovtHoliday("2026-05-28", "Id-ul-Zuha (Bakrid)", "Gazetted Holiday");
        addGovtHoliday("2026-06-26", "Muharram", "Gazetted Holiday");
        addGovtHoliday("2026-08-15", "Independence Day", "National Holiday");
        addGovtHoliday("2026-08-26", "Milad-un-Nabi (Id-e-Milad)", "Gazetted Holiday");
        addGovtHoliday("2026-09-04", "Janmashtami", "Gazetted Holiday");
        addGovtHoliday("2026-10-02", "Mahatma Gandhi Jayanti", "National Holiday");
        addGovtHoliday("2026-10-20", "Dussehra (Vijaya Dashami)", "Gazetted Holiday");
        addGovtHoliday("2026-11-08", "Diwali (Deepavali)", "Gazetted Holiday");
        addGovtHoliday("2026-11-09", "Govardhan Puja", "Government Holiday");
        addGovtHoliday("2026-11-10", "Bhai Duj", "Government Holiday");
        addGovtHoliday("2026-11-15", "Chhath Puja", "Government Holiday");
        addGovtHoliday("2026-11-24", "Guru Nanak Jayanti", "Gazetted Holiday");
        addGovtHoliday("2026-12-25", "Christmas Day", "Gazetted Holiday");

        // 2026 Tamil Nadu State Government Public Holidays
        addGovtHoliday("2026-01-14", "Pongal", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2026-01-15", "Thiruvalluvar Day", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2026-01-16", "Uzhavar Thirunal", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2026-04-14", "Tamil New Year / Puthandu", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2026-09-14", "Vinayakar Chathurthi", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2026-10-19", "Ayutha Pooja / Saraswathi Pooja", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2026-10-20", "Vijaya Dasami", "Tamil Nadu State Public Holiday");

        // 2025 Central Government Holidays
        addGovtHoliday("2025-01-26", "Republic Day", "National Holiday");
        addGovtHoliday("2025-02-26", "Maha Shivratri", "Gazetted Holiday");
        addGovtHoliday("2025-03-14", "Holi", "Gazetted Holiday");
        addGovtHoliday("2025-03-31", "Id-ul-Fitr", "Gazetted Holiday");
        addGovtHoliday("2025-04-10", "Mahavir Jayanti", "Gazetted Holiday");
        addGovtHoliday("2025-04-18", "Good Friday", "Gazetted Holiday");
        addGovtHoliday("2025-05-12", "Buddha Purnima", "Gazetted Holiday");
        addGovtHoliday("2025-06-07", "Id-ul-Zuha (Bakrid)", "Gazetted Holiday");
        addGovtHoliday("2025-07-06", "Muharram", "Gazetted Holiday");
        addGovtHoliday("2025-08-15", "Independence Day", "National Holiday");
        addGovtHoliday("2025-09-05", "Milad-un-Nabi", "Gazetted Holiday");
        addGovtHoliday("2025-10-02", "Mahatma Gandhi Jayanti", "National Holiday");
        addGovtHoliday("2025-10-20", "Diwali (Deepavali)", "Gazetted Holiday");
        addGovtHoliday("2025-11-05", "Guru Nanak Jayanti", "Gazetted Holiday");
        addGovtHoliday("2025-12-25", "Christmas Day", "Gazetted Holiday");

        // 2025 Tamil Nadu State Government Public Holidays
        addGovtHoliday("2025-01-14", "Pongal", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2025-01-15", "Thiruvalluvar Day", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2025-01-16", "Uzhavar Thirunal", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2025-04-14", "Tamil New Year / Puthandu", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2025-08-27", "Vinayakar Chathurthi", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2025-10-01", "Ayutha Pooja", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2025-10-02", "Vijaya Dasami", "Tamil Nadu State Public Holiday");

        // 2027 Central Government Holidays
        addGovtHoliday("2027-01-26", "Republic Day", "National Holiday");
        addGovtHoliday("2027-03-06", "Maha Shivratri", "Gazetted Holiday");
        addGovtHoliday("2027-03-23", "Holi", "Gazetted Holiday");
        addGovtHoliday("2027-03-11", "Id-ul-Fitr", "Gazetted Holiday");
        addGovtHoliday("2027-03-26", "Good Friday", "Gazetted Holiday");
        addGovtHoliday("2027-08-15", "Independence Day", "National Holiday");
        addGovtHoliday("2027-10-02", "Mahatma Gandhi Jayanti", "National Holiday");
        addGovtHoliday("2027-10-10", "Dussehra", "Gazetted Holiday");
        addGovtHoliday("2027-10-29", "Diwali", "Gazetted Holiday");
        addGovtHoliday("2027-11-13", "Guru Nanak Jayanti", "Gazetted Holiday");
        addGovtHoliday("2027-12-25", "Christmas Day", "Gazetted Holiday");

        // 2027 Tamil Nadu State Government Public Holidays
        addGovtHoliday("2027-01-14", "Pongal", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2027-01-15", "Thiruvalluvar Day", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2027-01-16", "Uzhavar Thirunal", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2027-04-14", "Tamil New Year / Puthandu", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2027-09-04", "Vinayakar Chathurthi", "Tamil Nadu State Public Holiday");
        addGovtHoliday("2027-10-09", "Ayutha Pooja", "Tamil Nadu State Public Holiday");
    }

    private void addGovtHoliday(String dateIso, String name, String description) {
        java.time.LocalDate d = java.time.LocalDate.parse(dateIso);
        if (!holidayRepository.existsByHolidayDateAndName(d, name)) {
            holidayRepository.save(CalendarHoliday.builder()
                    .holidayDate(d)
                    .name(name)
                    .type("GOVERNMENT")
                    .description(description)
                    .unexpected(false)
                    .build());
        }
    }
}

