# Keep data classes used by the contact/recents lists readable in crash reports.
-keep class com.mzazi.phone.Contact { *; }
-keep class com.mzazi.phone.RecentCall { *; }
-keep class kotlin.Metadata { *; }
