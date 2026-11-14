import UserNotifications

/// One repeating local notification. Nothing is sent over the network.
enum ReminderScheduler {
    private static let identifier = "daily-reminder"

    static func schedule(minuteOfDay: Int) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let m = Reminder.clamp(minuteOfDay)
            let content = UNMutableNotificationContent()
            content.title = "Time to breathe"
            content.body = "A few quiet minutes with OmLeaf."
            content.sound = .default
            var when = DateComponents()
            when.hour = m / 60
            when.minute = m % 60
            let trigger = UNCalendarNotificationTrigger(dateMatching: when, repeats: true)
            center.removePendingNotificationRequests(withIdentifiers: [identifier])
            center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
        }
    }

    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [identifier])
    }
}
