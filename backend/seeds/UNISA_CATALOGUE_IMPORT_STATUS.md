# UNISA catalogue import status

Verified against UNISA's official 2027 qualification listings and the 2026 stream-specific registration curriculum on 26 November 2026.

## Live Supabase catalogue

The live Student Module Hub database now contains **340 qualification records** linked to UNISA's eight qualification-awarding colleges. This includes the initial undergraduate batch, honours/postgraduate diploma and master's/doctoral listings, the separately added Advanced Diploma in Information Resource Management (90007), and the 2026 BEd Mathematics and English stream record.

| College | Qualification records |
|---|---:|
| College of Accounting Sciences | 38 |
| College of Agriculture and Environmental Sciences | 55 |
| College of Economic and Management Sciences | 68 |
| College of Education | 82 |
| College of Human Sciences | 15 |
| College of Law | 52 |
| College of Science, Engineering and Technology | 27 |
| Graduate School of Business Leadership | 3 |
| **Total** | **340** |

The second import added **91** college-confirmed honours and postgraduate diploma records. A third import added **122** master's and doctoral records using UNISA's official college-filtered listings and the 2027 exceptions page. All batches use duplicate checks by college and qualification code. The database import is idempotent by college and qualification code; rerunning the same batch does not create duplicates.

Four module records are loaded for **Bachelor of Arts Honours in Corrections Management (98655)** from the 2026 registration curriculum:

- HMPEN80 — Applied Research Methodology in Corrections Management
- HRPEN81 — Research Report in Corrections Management (co-requisite: HMPEN80)
- PEN4801 — Fundamental Penology
- PEN4804 — Strategic Corrections Management

The 2026 curriculum states that HMPEN80 replaces PEN4802 for students who have not already completed PEN4802. Students who completed PEN4802 retain that credit and must register HRPEN81.


## Verified 2026 module curriculum imports

A separate, repeatable SQL seed is committed at `backend/seeds/unisa_verified_module_curricula_2026.sql`. It skips existing rows by qualification and module code. The full seed was re-executed against the live database as an idempotency test; it inserted no duplicate rows.

- **Diploma in Policing (98220): 30 modules** imported from the 2026 qualification curriculum. The curriculum groups were preserved by NQF level (5, 6 and 7). Credits are recorded as 12 per module. The semester field is deliberately left blank because the qualification curriculum page does not identify each module's exact semester number. Selected prerequisite/recommendation notes are stored in the module description.
- **Advanced Diploma in Information Resource Management (90007): 10 modules** imported. The missing qualification record was added to the CSET catalogue. UNISA's subject catalogue identifies these modules as year modules, NQF 7 and 12 credits; semester/period is recorded as 0.
- **Diploma in Corrections Management (98218): 35 modules** imported from the 2026 qualification curriculum. Public Administration and Social Work group alternatives are retained separately in the module descriptions; students must follow one grouping rather than mixing them. Module prerequisites and recommendations from the page are retained. Credits are recorded as 12 and semester numbers are left blank where the page does not specify a period.
- **Bachelor of Laws (98680): 56 modules** imported from the current qualification curriculum. The page lists 12 credits per module; group A compulsory modules, the level-5 choose-one group, and the level-8 choose-two elective group are preserved in module descriptions. Prerequisite and recommendation notes shown on the qualification page are retained. Semester numbers are intentionally blank where the page only gives recommended ordering.
- **BEd Senior Phase and FET Teaching – Mathematics and English (90104-MTE): 50 module options** imported from the 2026 stream-specific curriculum. The stream is a separate qualification row. The language group is marked choose-one; credits and semester periods are left unspecified rather than inferred.
- **BA Honours in Corrections Management (98655): 4 modules** retained and enriched with the verified 2026 credits, NQF 8 and year/semester classification. HMPEN80 is a 12-credit semester module, with its exact semester number left unspecified; HRPEN81 is 36 credits and year-based; PEN4801 and PEN4804 are 24 credits and year-based.

The first six verified curriculum imports contained **185 curriculum-linked module records** across six qualifications/streams, plus one earlier module record associated with the pre-existing test data. The BEd Senior Phase and FET Teaching Mathematics and English stream (90104-MTE) contributes 50 listed module options across four NQF levels. It is stored as a separate stream-specific qualification so its subject combination is not mixed with other 90104 streams. The language communicative competence group is recorded as a choose-one group. Module credits and semester periods are left blank where the retrieved curriculum page does not specify them at module level. The Diploma in Corrections Management (98218) contributes 35 listed modules, including the Public Administration and Social Work alternative groupings at levels 6 and 7. Group-choice rules and the prerequisites/recommendations shown on the curriculum page are retained in module descriptions. The Bachelor of Laws (98680) contributes 56 listed modules: nine compulsory plus three level-5 options, ten level-6 modules, ten level-7 modules, eight level-8 compulsory modules and sixteen level-8 elective options. All are recorded as 12 credits; the group/choice rules and listed prerequisites are retained in each module description. The exact semester number is left blank because the qualification page provides recommended ordering rather than definitive semester periods. This remains a limited curriculum sample, not a complete module catalogue. Continue importing only when the exact qualification curriculum and, where needed, the official module subject entry can be checked.

Source for the BEd Mathematics and English stream curriculum: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Education-in-Senior-Phase-and-Further-Education-and-Training-Teaching-School-subject-combination%3A-Mathematics-and-English-%2890104-%E2%80%93-MTE%29
- Source for the Diploma in Corrections Management curriculum: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Diploma-in-Corrections-Management-%2898218%29
- Source for the Bachelor of Laws curriculum: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Laws-%2898680-%E2%80%93-NEW%29

Sources for the module batch:
- Diploma in Policing 2026 curriculum: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Diploma-in-Policing-%2898220%29
- Advanced Diploma in Information Resource Management 2026 curriculum: https://unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Advanced-Diploma-in-Information-Resource-Management-%2890007%29
- Information Resource Management subject/module catalogue: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Subjects-%26-modules/All-subjects/INFORMATION-RESOURCE-MANAGEMENT
- Policing subject/module catalogue: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Subjects-%26-modules/All-subjects/POLICING
- Corrections Management subject/module catalogue: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Subjects-%26-modules/All-subjects/CORRECTIONS-MANAGEMENT

## Completeness and accuracy boundary

This is a larger verified catalogue foundation, **not the complete UNISA qualification and module catalogue**. The imported 2027 qualification lists are admission listings, not a full curriculum database. Some college sections and postgraduate categories still need additional verification, and many module curricula have not yet been imported.

Do not manufacture module codes, prerequisites, semester designations, credit values or curriculum combinations. Add modules only after checking the correct qualification stream and academic year on the official UNISA registration page. Qualification streams with different module combinations must be represented separately.

The old Bachelor of Education (Senior and Further Education and Training Phases), code 02615, was phased out after 2024 and must not be presented as a current new-intake qualification. UNISA identifies the newer BEd Senior Phase and FET Teaching (90104) as a stream-based qualification; its module combinations differ by stream. The generic 90104 qualification row is present, and the Mathematics and English stream (90104-MTE) has been added separately. Other 90104 streams still require their own verified curriculum imports; do not assign MTE modules to them.

The 2027 BA Honours in Corrections Management page says updated registration/curriculum information will be available closer to the registration phase. The 98655 module rows currently represent the verified 2026 curriculum only.

## Official sources

- Undergraduate qualification listings: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Undergraduate-qualifications/Qualifications/All-qualifications
- Honours and postgraduate diploma all-colleges listing: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications
- Accounting Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=ACCOUNTING+SCIENCES
- Agriculture and Environmental Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=AGRICULTURE+AND+ENVIRONMENTAL+SCIENCES
- Economic and Management Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=ECONOMIC+AND+MANAGEMENT+SCIENCES
- Education honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=EDUCATION
- Human Sciences honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=HUMAN+SCIENCES
- Law honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=LAW
- Science, Engineering and Technology honours and PG diplomas: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Honours-degrees-%26-postgraduate-diplomas/Qualifications/All-qualifications?cw_college=SCIENCE%2C+ENGINEERING+AND+TECHNOLOGY
- Master's and doctoral qualifications: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Master%27s-%26-doctoral-degrees/Qualifications/All-qualifications
- Master's and doctoral 2027 exceptions (college-specific): https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Master%27s-%26-doctoral-degrees/Masters-doctoral-qualifications-exceptions
- Master's and doctoral qualification exceptions: https://www.unisa.ac.za/sites/corporate/default/Apply-for-admission/Master%27s-%26-doctoral-degrees/Masters-doctoral-qualifications-exceptions
- Undergraduate registration finder: https://www.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules
- UNISA 2026 student rules: https://www.unisa.ac.za/static/myunisa/Content/Learner%20support%20%26%20regions/Documents/Unisa%20Student%20Rules_2026.pdf
- BA Honours Corrections Management (2026 curriculum): https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Arts-Honours-in-Corrections-Management-%2898655%29


## Additional verified 2026 curricula — batch 2

Imported into the live database and committed as `backend/seeds/unisa_verified_module_curricula_2026_batch_2.sql`:

- **Higher Certificate in Criminal Justice (90006): 11 module options** — nine compulsory and two alternatives in the choose-one group.
- **Diploma in Security Management (98221): 38 module options** — level 5, 6 and 7 compulsory modules plus the official choose-two elective groups.
- **Bachelor of Arts in Criminology (98681): 55 module options** — levels 5, 6 and 7, preserving the Law, Corrections Management and Psychology pathways and the compulsory groups.

These 104 records are linked to the correct qualification rows. The curriculum pages do not specify semester numbers consistently, so semester remains blank. The official 2026 registration pages are stored as each module row's source URL. Re-running the seed is safe: existing rows are skipped by qualification and module code.

The live database now contains **290 module rows in total**, including **289 curriculum-linked modules across nine qualifications/streams** plus one earlier pre-existing test module row. The earlier six curriculum imports (185 modules) plus this batch (104 modules) account for the 289 curriculum-linked records. A database check reports **zero duplicate qualification/module-code pairs**.

Sources for batch 2:
- Higher Certificate in Criminal Justice: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Higher-Certificate-in-Criminal-Justice-%2890006%29
- Diploma in Security Management: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Diploma-in-Security-Management-%2898221%29
- BA in Criminology: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Arts-in-Criminology-%2898681%29


## Additional verified 2026 curricula — batch 3

Imported into live Supabase and committed as `backend/seeds/unisa_verified_module_curricula_2026_batch_3.sql`:

- **Bachelor of Arts in Forensic Science and Technology (90002): 46 module options** across NQF levels 5–7, with the official Law, Management and Criminology elective routes and listed prerequisites/co-requisites retained.
- **Higher Certificate in Accounting Sciences (98201): 10 compulsory modules** at NQF level 5.

The live database now contains **346 module rows**, of which **345 are curriculum-linked** across eleven qualifications/streams; one earlier module row is pre-existing test data. The batch 3 import adds 56 module records. Duplicate qualification/module-code key check remains zero. All 2026 curriculum source URLs are stored on their module rows. Semester is left blank where the qualification curriculum page does not specify it.

Sources:
- BA Forensic Science and Technology: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Arts-in-Forensic-Science-and-Technology-%2890002%29
- Higher Certificate in Accounting Sciences: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Higher-Certificate-in-Accounting-Sciences-%2898201%29


## Additional verified 2026 curriculum — batch 4

Imported and committed as `backend/seeds/unisa_verified_module_curricula_2026_batch_4.sql`:

- **Bachelor of Arts in Police Science (98683): 61 module options** across NQF levels 5–7. The compulsory core and the mutually exclusive Management, Public Administration, Law and Criminal Justice pathways are retained, including the same-pathway rule through level 7 and the listed prerequisites/co-requisites.

The live database now contains **407 module rows**, of which **406 are curriculum-linked across twelve qualifications/streams**; one earlier module row is pre-existing test data. The batch 4 import adds 61 records. Duplicate qualification/module-code key check remains zero. Semester is blank where the curriculum page does not specify the module period.

Source: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Arts-in-Police-Science-%2898683%29


## Additional verified 2026 curriculum — batch 5

Imported and committed as `backend/seeds/unisa_verified_module_curricula_2026_batch_5.sql`:

- **Bachelor of Commerce in Business Management (98310-BSM): 39 module options** across NQF levels 5–7. The seed includes the compulsory modules and all five-elective choices, preserving prerequisite and co-requisite notes.

The live database now contains **446 module rows**, of which **445 are curriculum-linked across thirteen qualifications/streams**; one earlier module row is pre-existing test data. The batch 5 import adds 39 records. Credits and semester periods are left blank where not specified in the curriculum page. Duplicate qualification/module-code key check is being rechecked after this import.

Source: https://m.unisa.ac.za/sites/corporate/default/Register-to-study-through-Unisa/Undergraduate-%26-honours-qualifications/Find-your-qualification-%26-choose-your-modules/All-qualifications/Bachelor-of-Commerce-in-Business-Management-%2898310-%E2%80%93-BSM%29
