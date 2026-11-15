package com.habit.tracker

// Generated from the original holiday tables
object HolData {
    class E(val en: String, val bn: String)
    val FIXED: Map<String, E> = mapOf(
        "02-21" to E("Shaheed Day & Intl Mother Language Day", "শহীদ দিবস ও আন্তর্জাতিক মাতৃভাষা দিবস"),
        "03-26" to E("Independence Day", "স্বাধীনতা দিবস"),
        "04-14" to E("Pahela Baishakh", "পহেলা বৈশাখ"),
        "05-01" to E("May Day", "মে দিবস"),
        "08-05" to E("July Uprising Day", "জুলাই গণঅভ্যুত্থান দিবস"),
        "12-16" to E("Victory Day", "বিজয় দিবস"),
        "12-25" to E("Christmas Day", "বড়দিন"),
    )
    val OTHER_FIXED: Map<String, E> = mapOf(
        "01-01" to E("New Year's Day", "ইংরেজি নববর্ষ"),
        "02-14" to E("Valentine's Day / Pohela Falgun", "ভ্যালেন্টাইনস ডে / পহেলা ফাল্গুন"),
        "03-08" to E("International Women's Day", "আন্তর্জাতিক নারী দিবস"),
        "03-25" to E("Genocide Remembrance Day", "গণহত্যা দিবস"),
        "05-08" to E("Rabindra Jayanti", "রবীন্দ্র জয়ন্তী"),
        "05-25" to E("Nazrul Jayanti", "নজরুল জয়ন্তী"),
        "07-01" to E("Bank holiday (half-yearly closing)", "ব্যাংক ছুটি (অর্ধবার্ষিক)"),
        "11-07" to E("National Revolution and Solidarity Day", "জাতীয় বিপ্লব ও সংহতি দিবস"),
        "12-14" to E("Martyred Intellectuals Day", "শহীদ বুদ্ধিজীবী দিবস"),
        "04-01" to E("April Fools' Day", "এপ্রিল ফুল দিবস"),
        "04-22" to E("Earth Day", "বিশ্ব ধরিত্রী দিবস"),
        "06-05" to E("World Environment Day", "বিশ্ব পরিবেশ দিবস"),
        "10-05" to E("World Teachers' Day", "বিশ্ব শিক্ষক দিবস"),
        "10-31" to E("Halloween", "হ্যালোইন"),
        "12-10" to E("Human Rights Day", "মানবাধিকার দিবস"),
        "12-24" to E("Christmas Eve", "বড়দিনের আগের দিন"),
        "12-31" to E("Bank holiday (yearly closing) / New Year's Eve", "ব্যাংক ছুটি (বার্ষিক) / ইংরেজি বর্ষের শেষ দিন"),
    )
    val YEARLY: Map<Int, Map<String, E>> = mapOf(
        2026 to mapOf(
            "02-04" to E("Shab-e-Barat", "শবে বরাত"),
            "03-17" to E("Shab-e-Qadr", "শবে কদর"),
            "03-18" to E("Eid-ul-Fitr holiday", "ঈদুল ফিতরের ছুটি"),
            "03-19" to E("Eid-ul-Fitr holiday", "ঈদুল ফিতরের ছুটি"),
            "03-20" to E("Jumatul Bida", "জুমাতুল বিদা"),
            "03-21" to E("Eid-ul-Fitr", "ঈদুল ফিতর"),
            "03-22" to E("Eid-ul-Fitr holiday", "ঈদুল ফিতরের ছুটি"),
            "03-23" to E("Eid-ul-Fitr holiday", "ঈদুল ফিতরের ছুটি"),
            "05-01" to E("Buddha Purnima", "বুদ্ধ পূর্ণিমা"),
            "05-26" to E("Eid-ul-Adha holiday", "ঈদুল আযহার ছুটি"),
            "05-27" to E("Eid-ul-Adha holiday", "ঈদুল আযহার ছুটি"),
            "05-28" to E("Eid-ul-Adha", "ঈদুল আযহা"),
            "05-29" to E("Eid-ul-Adha holiday", "ঈদুল আযহার ছুটি"),
            "05-30" to E("Eid-ul-Adha holiday", "ঈদুল আযহার ছুটি"),
            "05-31" to E("Eid-ul-Adha holiday", "ঈদুল আযহার ছুটি"),
            "06-26" to E("Ashura", "আশুরা"),
            "08-26" to E("Eid-e-Miladunnabi", "ঈদে মিলাদুন্নবী"),
            "09-04" to E("Janmashtami", "জন্মাষ্টমী"),
            "10-20" to E("Durga Puja (Mahanabami)", "দুর্গাপূজা (মহানবমী)"),
            "10-21" to E("Durga Puja (Bijoya Dashami)", "দুর্গাপূজা (বিজয়া দশমী)"),
            "10-22" to E("Durga Puja holiday", "দুর্গাপূজার ছুটি"),
        ),
    )
}
