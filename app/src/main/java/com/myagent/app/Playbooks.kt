package com.myagent.app

/**
 * Know-how the agent carries. Only the parts that match the user's task are added to the prompt,
 * so ordinary chats cost no extra tokens.
 */
object Playbooks {
    private val CODE = """
CODING: 1) Restate the goal and pick the simplest stack (single-file HTML/CSS/JS for sites and tools; Python for scripts). 2) Write complete, working code with the write action (small files, clear names). 3) Open it (view=true) and read the SCREEN to test it like a user would. 4) If something is wrong, change only the broken part and test again. 5) Finish by telling the user where the file is and how to run it.
DEBUGGING: reproduce the problem -> read the exact error text -> find the first line where reality differs from expectation -> form one hypothesis -> change one thing -> re-test. Check inputs, null/empty values, types, async timing, wrong paths/URLs, permissions. Never guess blindly; never claim fixed until re-tested."""

    private val SECURITY = """
BUG BOUNTY (authorised only): 1) Read the program's scope and rules first; test ONLY listed in-scope assets and only domains in the user's Allowed domains. 2) Recon: scan action, web search for the program policy, public docs. 3) Passive findings to look for: missing security headers, cookies without Secure/HttpOnly, exposed .git/.env, verbose errors, outdated server banners, open redirects and weak CORS visible in normal behaviour. 4) Verify manually and keep the proof minimal (URL, steps, response); never access, store or dump other people's data; no DoS, no brute force, no social engineering. 5) Check the finding is not a known/duplicate issue and is in scope. 6) Write the report: Title, Severity, Affected URL, Steps to reproduce, Impact, Suggested fix. 7) The USER submits the report; you only draft it. Never promise payouts."""

    private val BUSINESS = """
BUSINESS WORK: ads/marketing = clear offer, target customer, one call to action, 3 variants. Stories/content = hook first, short sections, user's language. Video editing = operate the editing app step by step (import, trim, captions, export) reading the screen each time. Customer outreach = honest, polite, no spam, no fake claims; the user approves anything sent."""

    fun forTask(task: String): String {
        val t = task.lowercase()
        val sb = StringBuilder()
        if (Regex("code|website|web site|html|python|script|program|app |bug|debug|error|fix|crash|css|javascript|\\bjs\\b|कोड").containsMatchIn(t)) sb.append(CODE)
        if (Regex("bounty|vulnerab|security|pentest|hackerone|bugcrowd|intigriti|scan|exploit|xss|sql").containsMatchIn(t)) sb.append(SECURITY)
        if (Regex("\\bad\\b|advert|marketing|customer|video|edit|story|content|market|business|client|campaign").containsMatchIn(t)) sb.append(BUSINESS)
        if (Regex("paise|paisa|kamao|kamana|earn|money|income|freelanc|gig|fiverr|upwork|bounty|कमा|पैसे").containsMatchIn(t)) sb.append(EARN)
        return sb.toString()
    }

    private const val EARN = """
EARNING PLAYBOOK: be honest, no guaranteed money, no fake claims, no spam. Real routes: (1) bug bounty on programs in the user's allowed scope (passive checks, careful verified report), (2) freelance gigs: search Fiverr/Upwork/Freelancer jobs matching coding/website/video-edit skills, draft a clear proposal and deliverable, and open the app for the user to submit, (3) sell small websites/landing pages to local businesses: build the page, write a short pitch message, (4) content: scripts, captions, thumbnails for creators. Pick the cheapest next step, do it, report what was done and what needs the user's OK. Never post, send or pay without the confirm panel."""
}
