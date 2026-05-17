package com.subaru.servicetool.data.model

enum class IssueSeverity { CRITICAL, HIGH, MEDIUM }

data class KnownIssue(
    val id: String,
    val name: LocalizedText,
    val dtcCodes: List<String>,
    val symptoms: LocalizedText,
    val diagnosticCheck: LocalizedText,
    val fix: LocalizedText,
    val severity: IssueSeverity,
    val requiresTcvMonitor: Boolean = false,
)

object KnownIssueRegistry {

    val ALL: List<KnownIssue> = listOf(

        KnownIssue(
            id = "CVT_AWD_SOLENOID",
            name = LocalizedText(
                en = "AWD Transfer Solenoid (Orange Wire)",
                ka = "AWD გადამცემი სოლენოიდი (ნარინჯისფერი კაბელი)",
                ru = "Соленоид передачи AWD (оранжевый провод)",
                es = "Solenoide de Transferencia AWD (Cable Naranja)",
                fr = "Solénoïde de Transfert AWD (Câble Orange)",
                de = "AWD-Übertragungs-Solenoid (Oranges Kabel)",
            ),
            dtcCodes = listOf("P0971", "P0700"),
            symptoms = LocalizedText(
                en = "Dashboard lights: AT OIL TEMP, ABS, Hill Assist, and Traction Control all illuminate simultaneously. Loss of AWD — rear wheels disengage.",
                ka = "სამართავი პანელის ნათურები: AT OIL TEMP, ABS, ციცაბო ადგილის ასისტენტი და ანტიდახლება ერთდროულად ანათებს. AWD-ის დაკარგვა — უკანა თვლები გამოირთობა.",
                ru = "Загораются индикаторы: AT OIL TEMP, ABS, Hill Assist и система стабилизации одновременно. Потеря полного привода — задние колёса отключаются.",
                es = "Luces del tablero: AT OIL TEMP, ABS, Asistente de Pendiente y Control de Tracción se iluminan simultáneamente. Pérdida de AWD — las ruedas traseras se desconectan.",
                fr = "Voyants tableau de bord : AT OIL TEMP, ABS, Aide en côte et Contrôle de traction s'allument simultanément. Perte de l'AWD — les roues arrière se désengagent.",
                de = "Kontrollleuchten: AT OIL TEMP, ABS, Bergfahrassistent und Stabilitätsprogramm leuchten gleichzeitig. AWD-Verlust — Hinterräder trennen sich.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Read live TCU data. Check solenoid resistance: should be 3–4 Ω. Open circuit = failed solenoid.",
                ka = "წაიკითხეთ TCU-ს ცოცხალი მონაცემები. შეამოწმეთ სოლენოიდის წინაღობა: უნდა იყოს 3–4 Ω. ღია ჩართვა = გაფუჭებული სოლენოიდი.",
                ru = "Считать живые данные ТСМ. Проверить сопротивление соленоида: должно быть 3–4 Ом. Обрыв цепи = неисправный соленоид.",
                es = "Leer datos en vivo del TCU. Verificar resistencia del solenoide: debe ser 3–4 Ω. Circuito abierto = solenoide defectuoso.",
                fr = "Lire les données en direct du TCU. Vérifier la résistance du solénoïde : doit être 3–4 Ω. Circuit ouvert = solénoïde défectueux.",
                de = "Live-TCU-Daten auslesen. Solenoidwiderstand prüfen: soll 3–4 Ω sein. Unterbrechung = defekter Solenoid.",
            ),
            fix = LocalizedText(
                en = "Replace AWD transfer solenoid in CVT valve body. Part: 31706AA030/031/032/033. After replacement: perform CVT relearn procedure.",
                ka = "შეცვალეთ AWD-ის გადამცემი სოლენოიდი CVT-ის სარქვლის ბლოკში. ნაწილი: 31706AA030/031/032/033. შეცვლის შემდეგ: ჩაუტარეთ CVT-ის ხელახალი სწავლების პროცედურა.",
                ru = "Заменить соленоид привода AWD в гидравлическом блоке CVT. Артикул: 31706AA030/031/032/033. После замены: выполнить процедуру обучения CVT.",
                es = "Reemplazar solenoide de transferencia AWD en el cuerpo de válvulas del CVT. Pieza: 31706AA030/031/032/033. Después del reemplazo: realizar procedimiento de reaprendizaje del CVT.",
                fr = "Remplacer le solénoïde de transfert AWD dans le corps de valve CVT. Pièce: 31706AA030/031/032/033. Après remplacement : effectuer la procédure d'apprentissage CVT.",
                de = "AWD-Übertragungs-Solenoid im CVT-Ventilblock ersetzen. Teilenummer: 31706AA030/031/032/033. Nach dem Austausch: CVT-Lernprozedur durchführen.",
            ),
            severity = IssueSeverity.CRITICAL,
        ),

        KnownIssue(
            id = "CVT_LOCKUP_SOLENOID",
            name = LocalizedText(
                en = "Torque Converter Lock-Up Solenoid",
                ka = "ბრუნვის გამდიდრებლის ჩაკეტვის სოლენოიდი",
                ru = "Соленоид блокировки гидротрансформатора",
                es = "Solenoide de Bloqueo del Convertidor de Par",
                fr = "Solénoïde de Verrouillage du Convertisseur de Couple",
                de = "Drehmomentwandler-Überbrückungs-Solenoid",
            ),
            dtcCodes = listOf("P2763", "P2764", "P0700"),
            symptoms = LocalizedText(
                en = "Jerking/shuddering when slowing to a stop. Engine wants to stall at low speed. AT OIL TEMP light flashes.",
                ka = "შეკრთომა/გაბზარვა გაჩერებამდე შენელებისას. ძრავა ჩაქრობას ცდილობს დაბალ სიჩქარეზე. AT OIL TEMP ნათური ციმციმებს.",
                ru = "Рывки/вибрация при замедлении до остановки. Двигатель пытается заглохнуть на низкой скорости. Мигает индикатор AT OIL TEMP.",
                es = "Sacudidas/vibraciones al reducir la velocidad hasta detenerse. El motor quiere apagarse a baja velocidad. La luz AT OIL TEMP parpadea.",
                fr = "Secousses/vibrations lors du freinage jusqu'à l'arrêt. Le moteur veut caler à basse vitesse. Le voyant AT OIL TEMP clignote.",
                de = "Rucken/Vibrieren beim Abbremsen bis zum Stillstand. Motor droht bei niedriger Drehzahl abzuwürgen. AT OIL TEMP-Leuchte blinkt.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Check solenoid resistance: should be 12–13 Ω at 20°C. Heat solenoid — if resistance goes to open circuit when hot, solenoid is faulty.",
                ka = "შეამოწმეთ სოლენოიდის წინაღობა: უნდა იყოს 12–13 Ω 20°C-ზე. გაათბეთ სოლენოიდი — თუ გათბობის შემდეგ წინაღობა ხდება ღია, სოლენოიდი გაფუჭებულია.",
                ru = "Проверить сопротивление соленоида: должно быть 12–13 Ом при 20°C. Нагреть соленоид — если при нагреве сопротивление уходит в обрыв, соленоид неисправен.",
                es = "Verificar resistencia del solenoide: debe ser 12–13 Ω a 20°C. Calentar el solenoide — si la resistencia se va a circuito abierto en caliente, el solenoide está defectuoso.",
                fr = "Vérifier la résistance du solénoïde: doit être 12–13 Ω à 20°C. Chauffer le solénoïde — si la résistance passe en circuit ouvert à chaud, le solénoïde est défectueux.",
                de = "Solenoidwiderstand prüfen: soll 12–13 Ω bei 20°C betragen. Solenoid aufwärmen — wenn der Widerstand beim Erwärmen auf Unterbrechung geht, ist der Solenoid defekt.",
            ),
            fix = LocalizedText(
                en = "Replace lock-up solenoid in valve body. Resistance spec: 12 Ω. After replacement: perform CVT fluid change + relearn.",
                ka = "შეცვალეთ ჩაკეტვის სოლენოიდი სარქვლის ბლოკში. წინაღობის სპეციფ.: 12 Ω. შეცვლის შემდეგ: CVT-ის სითხის შეცვლა + ხელახალი სწავლება.",
                ru = "Заменить соленоид блокировки в гидравлическом блоке. Спецификация сопротивления: 12 Ом. После замены: замена масла CVT + обучение.",
                es = "Reemplazar solenoide de bloqueo en el cuerpo de válvulas. Especificación de resistencia: 12 Ω. Después del reemplazo: cambio de fluido CVT + reaprendizaje.",
                fr = "Remplacer le solénoïde de verrouillage dans le corps de valve. Spécification de résistance: 12 Ω. Après remplacement: vidange fluide CVT + apprentissage.",
                de = "Überbrückungs-Solenoid im Ventilblock ersetzen. Widerstandsspezifikation: 12 Ω. Nach dem Austausch: CVT-Ölwechsel + Lernprozedur.",
            ),
            severity = IssueSeverity.HIGH,
        ),

        KnownIssue(
            id = "TCV_THERMOSTAT",
            name = LocalizedText(
                en = "Thermo Control Valve (TCV) — Electric Thermostat",
                ka = "თერმული კონტროლის სარქველი (TCV) — ელექტრო თერმოსტატი",
                ru = "Клапан термического управления (TCV) — электронный термостат",
                es = "Válvula de Control Térmico (TCV) — Termostato Eléctrico",
                fr = "Vanne de Contrôle Thermique (TCV) — Thermostat Électrique",
                de = "Thermisches Steuerventil (TCV) — Elektrischer Thermostat",
            ),
            dtcCodes = listOf("P0128"),
            symptoms = LocalizedText(
                en = "Engine takes too long to warm up. Poor fuel economy. Heater blows cool air. EyeSight may disable in cold weather.",
                ka = "ძრავა ძალიან დიდ ხანს ათბობს. საწვავის ცუდი ხარჯი. გამათბობელი ცივ ჰაერს ბერავს. EyeSight შეიძლება გამოირთოს ცივ ამინდში.",
                ru = "Двигатель долго прогревается. Повышенный расход топлива. Печка дует холодным воздухом. EyeSight может отключиться в холодную погоду.",
                es = "El motor tarda demasiado en calentarse. Mayor consumo de combustible. La calefacción sopla aire frío. EyeSight puede desactivarse en clima frío.",
                fr = "Le moteur met trop longtemps à chauffer. Mauvaise consommation de carburant. Le chauffage souffle de l'air froid. EyeSight peut se désactiver par temps froid.",
                de = "Motor braucht zu lange zum Aufwärmen. Schlechter Kraftstoffverbrauch. Heizung bläst kalte Luft. EyeSight kann bei Kälte deaktiviert werden.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Monitor live coolant temp. If temp rises very slowly or drops at highway speeds — TCV failed. Check for TSB 09-75-21 ECM update first.",
                ka = "აკვირდებით გამაგრილებლის ცოცხალ ტემპერატურას. თუ ტემპ. ძალიან ნელა იზრდება ან ეცემა გზატკეცილზე — TCV გაფუჭებულია. ჯერ შეამოწმეთ TSB 09-75-21 ECM განახლება.",
                ru = "Контролировать живую температуру охлаждающей жидкости. Если темп. растёт очень медленно или падает на шоссе — TCV неисправен. Сначала проверить обновление ЭБУ TSB 09-75-21.",
                es = "Monitorear la temperatura del refrigerante en tiempo real. Si la temp. sube muy lentamente o cae en autopista — TCV defectuoso. Verificar primero la actualización ECM TSB 09-75-21.",
                fr = "Surveiller la température du liquide de refroidissement en temps réel. Si la temp. monte très lentement ou chute à vitesse autoroute — TCV défaillant. Vérifier d'abord la mise à jour ECM TSB 09-75-21.",
                de = "Kühlmitteltemperatur in Echtzeit überwachen. Wenn Temp. sehr langsam steigt oder auf Autobahn fällt — TCV defekt. Zuerst ECM-Update TSB 09-75-21 prüfen.",
            ),
            fix = LocalizedText(
                en = "1. First: check TSB 09-75-21 — free ECM software update at dealer. 2. If still fails: replace Thermo Control Valve assembly. WARRANTY: Subaru extended to 15 years/150,000 miles.",
                ka = "1. ჯერ: შეამოწმეთ TSB 09-75-21 — უფასო ECM პროგრამული განახლება დილერთან. 2. თუ კვლავ გრძელდება: შეცვალეთ TCV კვანძი. გარანტია: Subaru-მ გაახანგრძლივა 15 წლამდე/150,000 მილამდე.",
                ru = "1. Сначала: проверить TSB 09-75-21 — бесплатное обновление ПО ЭБУ у дилера. 2. Если проблема остаётся: заменить клапан TCV. ГАРАНТИЯ: Subaru продлила до 15 лет/150,000 миль.",
                es = "1. Primero: verificar TSB 09-75-21 — actualización gratuita de software ECM en concesionario. 2. Si continúa fallando: reemplazar conjunto de Válvula TCV. GARANTÍA: Subaru extendió a 15 años/150,000 millas.",
                fr = "1. D'abord: vérifier TSB 09-75-21 — mise à jour logicielle ECM gratuite chez le concessionnaire. 2. Si toujours défaillant: remplacer l'ensemble TCV. GARANTIE: Subaru a étendu à 15 ans/150 000 miles.",
                de = "1. Zuerst: TSB 09-75-21 prüfen — kostenlose ECM-Software-Aktualisierung beim Händler. 2. Wenn weiterhin defekt: TCV-Baugruppe ersetzen. GARANTIE: Subaru auf 15 Jahre/150.000 Meilen verlängert.",
            ),
            severity = IssueSeverity.MEDIUM,
            requiresTcvMonitor = true,
        ),

        KnownIssue(
            id = "CVT_ASCENT_PRESSURE",
            name = LocalizedText(
                en = "CVT Pressure Control Issue",
                ka = "CVT-ის წნევის კონტროლის პრობლემა",
                ru = "Проблема управления давлением CVT",
                es = "Problema de Control de Presión CVT",
                fr = "Problème de Contrôle de Pression CVT",
                de = "CVT-Drucksteuerungsproblem",
            ),
            dtcCodes = listOf("P0841", "P0868", "P0700"),
            symptoms = LocalizedText(
                en = "Transmission shudder, hesitation, and limp mode.",
                ka = "გადამცემის ვიბრაცია, დაყოვნება და გადარჩენის რეჟიმი.",
                ru = "Вибрация трансмиссии, задержки и аварийный режим.",
                es = "Vibración de transmisión, dudas y modo de emergencia.",
                fr = "Vibrations de la transmission, hésitations et mode de sécurité.",
                de = "Getriebebeben, Zögerlichkeit und Notlaufprogramm.",
            ),
            diagnosticCheck = LocalizedText(
                en = "Check transmission fluid level and condition. Scan for stored DTCs in TCU.",
                ka = "შეამოწმეთ გადამცემის სითხის დონე და მდგომარეობა. TCU-ში შეინახული DTC-ების სკანირება.",
                ru = "Проверить уровень и состояние масла в трансмиссии. Считать сохранённые коды ошибок в ТСМ.",
                es = "Verificar nivel y condición del fluido de transmisión. Escanear los DTCs almacenados en el TCU.",
                fr = "Vérifier le niveau et l'état du liquide de transmission. Scanner les DTCs mémorisés dans le TCU.",
                de = "Getriebeflüssigkeitsstand und -zustand prüfen. Gespeicherte DTCs im TCU auslesen.",
            ),
            fix = LocalizedText(
                en = "Check Subaru warranty extension (Hickman v. Subaru settlement 2024 — 100,000 mile warranty extension for affected Ascents).",
                ka = "შეამოწმეთ Subaru-ს გარანტიის გახანგრძლივება (Hickman v. Subaru 2024 შეთანხმება — 100,000 მილის გარანტიის გახანგრძლივება დაზიანებული Ascent-ებისთვის).",
                ru = "Проверить продление гарантии Subaru (мировое соглашение Hickman v. Subaru 2024 — продление гарантии на 100 000 миль для затронутых Ascent).",
                es = "Verificar extensión de garantía de Subaru (acuerdo Hickman v. Subaru 2024 — extensión de garantía de 100,000 millas para Ascents afectados).",
                fr = "Vérifier l'extension de garantie Subaru (accord Hickman v. Subaru 2024 — extension de garantie de 100 000 miles pour les Ascent concernés).",
                de = "Subaru-Garantieverlängerung prüfen (Hickman v. Subaru Vergleich 2024 — 100.000-Meilen-Garantieverlängerung für betroffene Ascents).",
            ),
            severity = IssueSeverity.HIGH,
        ),
    )

    private val byId = ALL.associateBy { it.id }

    fun findById(id: String): KnownIssue? = byId[id]

    fun findByDtcCode(code: String): KnownIssue? =
        ALL.firstOrNull { code in it.dtcCodes }
}
