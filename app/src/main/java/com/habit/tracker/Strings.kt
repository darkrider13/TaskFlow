package com.habit.tracker

/** Language helper: same texts as the original app (Bangla + English). */
object Str {
    val isBn: Boolean get() = Store.set.optString("lang", "bn") == "bn"

    fun t(k: String): String {
        val m = if (isBn) StrData.bn else StrData.en
        return m[k] ?: StrData.en[k] ?: k
    }

    /** Western digits -> Bangla digits (only in Bangla mode). */
    fun dig(x: Any?): String {
        val s = x?.toString() ?: ""
        if (!isBn) return s
        val sb = StringBuilder()
        for (c in s) sb.append(if (c in '0'..'9') "০১২৩৪৫৬৭৮৯"[c - '0'] else c)
        return sb.toString()
    }

    fun bn(n: Number): String = dig(n.toString())

    fun pad2(n: Int): String = n.toString().padStart(2, '0')

    /** Every time shown to the user is 12-hour. */
    fun t12(v: String?): String {
        if (v.isNullOrBlank()) return ""
        val p = v.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: 0
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        val h12 = if (h % 12 == 0) 12 else h % 12
        val mm = pad2(m)
        return if (!isBn) {
            "$h12:$mm" + (if (h < 12) " AM" else " PM")
        } else {
            dig(pad2(h12) + ":" + mm) + (if (h < 12) " এএম" else " পিএম")
        }
    }

    /** Texts that take values (these were functions in the original language table). */
    fun f(k: String, vararg a: Any?): String {
        val b = isBn
        fun x(i: Int) = a.getOrNull(i)?.toString() ?: ""
        return when (k) {
            "monthGoal" -> if (b) "লক্ষ্য: ${x(0)}/${x(1)} দিন" else "Goal: ${x(0)}/${x(1)} days"
            "totalGoal" -> if (b) "মোট: ${x(0)}/${x(1)} দিন" else "Total: ${x(0)}/${x(1)} days"
            "did" -> if (b) "${x(0)} করেছি" else "I did ${x(0)}"
            "confirmDel" -> if (b) "\"${x(0)}\" এবং এর সব টিক মুছে যাবে। আপনি কি নিশ্চিত?" else "\"${x(0)}\" and all its ticks will be deleted. Are you sure?"
            "wgTot" -> if (b) "আপনি এই অভ্যাসে ইতিমধ্যে ${x(0)} দিন পূর্ণ করে ফেলেছেন, তাই মোট লক্ষ্য ${x(0)} দিনের কম দেওয়া যাবে না। অনুগ্রহ করে ${x(0)} বা তার বেশি দিন।" else "You have already completed ${x(0)} days of this habit, so the total goal can't be lower than ${x(0)}. Please set it to ${x(0)} or more."
            "notifB" -> if (b) "আজকের ${x(0)} সম্পন্ন হয়েছে কি?" else "Did you complete today's ${x(0)}?"
            "todoNotifB" -> if (b) "${x(0)} - কাজটি করেছেন কি?" else "${x(0)} - did you do it?"
            "every" -> if (b) "প্রতিদিন ${x(0)}-এ" else "Every day at ${x(0)}"
            "tasksDone" -> if (b) "আজ ${x(1)}টির মধ্যে ${x(0)}টি করণীয় সম্পন্ন" else "${x(0)} of ${x(1)} tasks done today"
            "remInfo" -> if (b) "প্রতি ${dig(x(0))} · ${dig(x(1))}–${dig(x(2))}" else "Every ${x(0)} · ${x(1)}–${x(2)}"
            "habSub" -> if (b) "${x(0)} দিনের মধ্যে ${x(1)} দিন" else "${x(1)} of ${x(0)} days"
            "tooEarly" -> if (b) "আজকের দিন ${x(0)}-এর আগে টিক দেওয়া যাবে না। রিমাইন্ডারের সময় হলে দিন।" else "You can mark today only after ${x(0)}. Until then, wait for the reminder."
            "remMax" -> if (b) "এই ব্যবধানে শুরু থেকে শেষ সর্বোচ্চ ${dig(x(0))} হতে পারে" else "Too many reminders. With this gap, From to Until can be at most ${x(0)}"
            "cxLeft" -> if (b) "${x(0)} দিন বাকি" else "${x(0)} day${if (x(0) == "1") "" else "s"} left"
            "ovN" -> if (b) "${x(0)}টি কাজ বাকি পড়ে আছে" else "${x(1)} ${if (x(1) == "1") "task is" else "tasks are"} overdue"
            "rHero" -> if (b) "নির্ধারিত অভ্যাসের ${x(0)}% সম্পন্ন" else "${x(0)}% of scheduled habit days done"
            "rTasks" -> if (b) "করণীয়: ${x(1)}টির মধ্যে ${x(0)}টি সম্পন্ন" else "Tasks: ${x(0)} of ${x(1)} done"
            "bTotal" -> if (b) "${dig(x(0))}টি টিক" else "${x(0)} ticks"
            "bTask" -> if (b) "${dig(x(0))}টি করণীয় সম্পন্ন" else "${x(0)} tasks done"
            "fToday" -> if (b) "সেশন সম্পন্ন: ${x(0)}" else "Session completed: ${x(0)}"
            "onDays" -> if (b) "${x(0)} — ${x(1)}-এ" else "${x(0)} at ${x(1)}"
            "bkConf" -> if (b) "এই ব্যাকআপে ${x(0)}টি অভ্যাস ও ${x(1)}টি করণীয় আছে। বর্তমান সব ডেটা মুছে এটি বসানো হবে। নিশ্চিত?" else "This backup has ${x(0)} habits and ${x(1)} tasks. All current data will be replaced. Continue?"
            "bkDoneD" -> if (b) "${dig(x(0))}টি অভ্যাস ও ${dig(x(1))}টি করণীয় ইম্পোর্ট হয়েছে" else "${x(0)} habits and ${x(1)} tasks imported"
            "exDoneD" -> if (b) "${dig(x(0))}টি অভ্যাস ও ${dig(x(1))}টি করণীয় এক্সপোর্ট হয়েছে" else "${x(0)} habits and ${x(1)} tasks exported"
            "exSavedAt" -> if (b) "${x(0)} এ সেভ হয়েছে" else "Saved in ${x(0)}"
            else -> k
        }
    }
}

fun Str.arr(k: String): List<String> = t(k).split("|")
