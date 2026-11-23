package com.example.data.catalog

import com.example.domain.model.CaliperResolution
import com.example.domain.model.EvaluationCategory
import com.example.domain.model.InstrumentType
import com.example.domain.model.LessonSection
import com.example.domain.model.LessonTopic
import com.example.domain.model.MachiningChallenge
import com.example.domain.model.QuizQuestion

object LearningAndQuizCatalog {

    val lessonTopics: List<LessonTopic> = listOf(
        LessonTopic(
            id = "topic_komponen_caliper",
            title = "1. Komponen & Fungsi Jangka Sorong",
            subtitle = "Rahang luar, rahang dalam, skala utama, nonius, lock screw, dan depth rod",
            category = "Jangka Sorong",
            relatedInstrument = InstrumentType.MANUAL_CALIPER,
            objectives = listOf(
                "Mengidentifikasi seluruh komponen utama jangka sorong manual dan digital.",
                "Menjelaskan fungsi rahang luar, rahang dalam, batang kedalaman, dan pengunci.",
                "Membedakan cara kerja skala nonius dengan sensor kapasitif LCD digital."
            ),
            sections = listOf(
                LessonSection(
                    heading = "Bagian-Bagian Utama Jangka Sorong",
                    body = "Jangka sorong (Vernier Caliper) adalah alat ukur presisi serbaguna dengan rentang umum 0–150 mm yang terbuat dari baja tahan karat (stainless hardened).",
                    keyPoints = listOf(
                        "Rahang Luar (Outside Jaws): Mengukur diameter luar, panjang, atau tebal benda kerja.",
                        "Rahang Dalam (Inside Jaws): Mengukur diameter lubang dalam atau lebar celah.",
                        "Batang Kedalaman (Depth Rod): Mengukur kedalaman lubang buta dan step/tingkat.",
                        "Skala Utama & Skala Nonius: Menunjukkan nilai milimeter utuh dan pecahan ketelitian (0,05 / 0,02 mm).",
                        "Sekrup Pengunci (Lock Screw): Mengunci posisi rahang geser agar hasil tidak berubah saat dibaca.",
                        "Roda Penggerak (Thumb Wheel): Memudahkan penggeseran halus dengan ibu jari."
                    )
                ),
                LessonSection(
                    heading = "Perbedaan Jangka Sorong Digital",
                    body = "Pada jangka sorong digital, pergeseran rahang dibaca oleh sensor linier kapasitif di sepanjang batang utama dan ditampilkan langsung ke layar LCD dengan ketelitian 0,01 mm.",
                    keyPoints = listOf(
                        "Tombol ON/OFF: Menghidupkan/mematikan tampilan LCD.",
                        "Tombol ZERO: Menyetel titik nol (0,00 mm) pada posisi rahang mana pun.",
                        "Tombol mm/inch: Mengonversi pembacaan antara metrik (mm) dan imperial (inci).",
                        "Tombol HOLD: Menahan angka di layar saat membaca pada posisi sulit."
                    )
                )
            )
        ),
        LessonTopic(
            id = "topic_komponen_micrometer",
            title = "2. Komponen & Fungsi Mikrometer Sekrup",
            subtitle = "Frame, anvil, spindle, sleeve, thimble, ratchet stop, dan lock lever",
            category = "Mikrometer",
            relatedInstrument = InstrumentType.MANUAL_MICROMETER,
            objectives = listOf(
                "Menjelaskan fungsi setiap bagian mikrometer sekrup manual dan digital.",
                "Memahami batas penggunaan mikrometer luar standar (hanya diameter luar & ketebalan).",
                "Menggunakan ratchet stop untuk menjaga gaya pengukuran konstan."
            ),
            sections = listOf(
                LessonSection(
                    heading = "Struktur Mekanis Mikrometer Luar (0–25 mm)",
                    body = "Mikrometer sekrup memanfaatkan mekanisme ulir presisi dengan pitch 0,5 mm. Satu putaran penuh thimble (360°) memajukan atau memundurkan spindle tepat 0,5 mm.",
                    keyPoints = listOf(
                        "Frame (Rangka U): Struktur kaku berlapis isolator panas agar pemuaian tangan tidak memengaruhi hasil.",
                        "Anvil (Landasan Tetap): Bidang ukur tetap berujung karbida tahan aus.",
                        "Spindle (Poros Ukur Gerak): Bergerak linier sejajar sumbu ulir saat thimble diputar.",
                        "Sleeve (Skala Utama): Memiliki garis skala atas tiap 1 mm dan garis bawah tiap 0,5 mm.",
                        "Thimble (Skala Putar): Terbagi menjadi 50 garis; tiap 1 garis bernilai 0,01 mm.",
                        "Ratchet Stop: Pembatas gaya ukur; berbunyi klik 2–3 kali saat gaya kontak ideal tercapai.",
                        "Lock Lever: Mengunci spindle sebelum pembacaan skala."
                    )
                ),
                LessonSection(
                    heading = "Batas Penggunaan Alat",
                    body = "Mikrometer luar standar hanya dirancang untuk diameter luar dan ketebalan pada rentang 0–25 mm. Pengukuran diameter lubang dalam memerlukan Inside Micrometer, dan kedalaman memerlukan Depth Micrometer."
                )
            )
        ),
        LessonTopic(
            id = "topic_prinsip_kerja",
            title = "3. Prinsip Kerja & Kinematika Skala",
            subtitle = "Hubungan selisih skala nonius dan pitch ulir terhadap ketelitian alat",
            category = "Prinsip Kerja",
            relatedInstrument = InstrumentType.MANUAL_CALIPER,
            objectives = listOf(
                "Memahami asal nilai ketelitian 0,05 mm dan 0,02 mm pada jangka sorong manual.",
                "Memahami hubungan pitch ulir 0,5 mm dan 50 skala thimble menghasilkan ketelitian 0,01 mm."
            ),
            sections = listOf(
                LessonSection(
                    heading = "Prinsip Skala Nonius (Vernier)",
                    body = "Ketelitian jangka sorong berasal dari selisih antara satu bagian skala utama dengan satu bagian skala nonius. Semakin banyak pembagian skala nonius (20 untuk 0,05 mm atau 50 untuk 0,02 mm), semakin halus ketelitiannya.",
                    formulaHighlight = "Ketelitian = 1 mm / Jumlah Pembagian Skala Nonius"
                ),
                LessonSection(
                    heading = "Prinsip Ulir Presisi Mikrometer",
                    body = "Mikrometer memiliki ulir berjarak puncak (pitch) 0,5 mm dan 50 garis pada keliling thimble. Setiap pergeseran 1 garis thimble menggerakkan spindle sebesar 0,5 mm / 50 = 0,01 mm.",
                    formulaHighlight = "Ketelitian Mikrometer = Pitch (0,5 mm) / 50 = 0,01 mm"
                )
            )
        ),
        LessonTopic(
            id = "topic_prosedur",
            title = "4. Prosedur Standar Pengukuran (SOP)",
            subtitle = "Urutan kerja bengkel pemesinan yang benar dan aman",
            category = "Prosedur",
            relatedInstrument = InstrumentType.DIGITAL_CALIPER,
            objectives = listOf(
                "Menerapkan urutan persiapan, penempatan benda kerja, penjepitan, penguncian, dan pembacaan.",
                "Mencegah kerusakan alat ukur dan kesalahan prosedur di bengkel."
            ),
            sections = listOf(
                LessonSection(
                    heading = "5 Langkah Standar Pengukuran Bengkel",
                    body = "Setiap pengukuran presisi wajib mengikuti urutan kerja baku agar data valid dan alat awet.",
                    keyPoints = listOf(
                        "1. Pembersihan: Bersihkan permukaan benda kerja serta bidang ukur rahang/anvil dari oli dan gram.",
                        "2. Pengecekan Nol: Rapatkan rahang atau spindle (pakai ratchet) dan pastikan menunjukkan 0,00 mm.",
                        "3. Penjepitan Tegak Lurus: Posisikan benda kerja sejajar bidang ukur tanpa kemiringan dan gaya berlebih.",
                        "4. Penguncian: Aktifkan Lock Screw atau Lock Lever sebelum melepas benda kerja.",
                        "5. Pembacaan Tegak Lurus Mata: Baca skala lurus dari depan untuk menghindari kesalahan paralaks."
                    )
                )
            )
        ),
        LessonTopic(
            id = "topic_baca_skala",
            title = "5. Cara Membaca Skala & Rumus Perhitungan",
            subtitle = "Langkah sistematis membaca Jangka Sorong dan Mikrometer Manual",
            category = "Pembacaan Skala",
            relatedInstrument = InstrumentType.MANUAL_MICROMETER,
            objectives = listOf(
                "Menghitung hasil ukur jangka sorong dari skala utama dan garis nonius berimpit.",
                "Menghitung hasil ukur mikrometer dari skala sleeve (termasuk garis 0,5 mm) dan skala thimble."
            ),
            sections = listOf(
                LessonSection(
                    heading = "Rumus Jangka Sorong Manual",
                    body = "Baca nilai skala utama tepat di sebelah kiri angka '0' skala nonius. Kemudian cari satu garis nonius yang paling sejajar (berimpit) dengan garis skala utama.",
                    formulaHighlight = "Hasil = Skala Utama + (Nomor Garis Nonius Berimpit × Ketelitian)",
                    keyPoints = listOf(
                        "Contoh (ketelitian 0,05 mm): Skala utama = 25 mm, garis nonius ke-10 berimpit.",
                        "Hasil = 25 + (10 × 0,05) = 25,50 mm."
                    )
                ),
                LessonSection(
                    heading = "Rumus Mikrometer Sekrup Manual",
                    body = "Baca garis skala atas (kelipatan 1 mm) yang terlihat di kiri tepi thimble. Periksa apakah garis 0,5 mm di bagian bawah sudah tampak. Lalu baca angka thimble yang sejajar garis datum horizontal.",
                    formulaHighlight = "Hasil = Skala Sleeve + (Skala Thimble × 0,01 mm)",
                    keyPoints = listOf(
                        "Contoh: Garis atas terlihat 10 mm, garis bawah 0,5 mm belum tampak → Sleeve = 10,0 mm.",
                        "Garis datum sejajar angka 28 di thimble → 28 × 0,01 = 0,28 mm.",
                        "Total = 10,0 + 0,28 = 10,28 mm."
                    )
                )
            )
        ),
        LessonTopic(
            id = "topic_kalibrasi",
            title = "6. Kalibrasi & Zero Setting",
            subtitle = "Deteksi zero error positif/negatif dan penyetelan titik nol",
            category = "Kalibrasi",
            relatedInstrument = InstrumentType.DIGITAL_MICROMETER,
            objectives = listOf(
                "Melakukan pengecekan titik nol pada alat manual maupun digital.",
                "Menghitung koreksi hasil ukur bila terdapat zero error positif atau negatif."
            ),
            sections = listOf(
                LessonSection(
                    heading = "Zero Error Positif & Negatif",
                    body = "Zero error terjadi apabila rahang/spindle telah dirapatkan sempurna namun skala tidak menunjukkan tepat 0,00 mm.",
                    formulaHighlight = "Hasil Terkoreksi = Hasil Pembacaan − Zero Error",
                    keyPoints = listOf(
                        "Zero Error Positif (+): Garis nol nonius/thimble berada di atas/kanan nol → kurangi dari hasil baca.",
                        "Zero Error Negatif (−): Garis nol berada di bawah/kiri nol → tambahkan ke hasil baca.",
                        "Pada alat digital, cukup rapatkan rahang bersih lalu tekan tombol ZERO untuk menyetel 0,00 mm."
                    )
                )
            )
        ),
        LessonTopic(
            id = "topic_kesalahan",
            title = "7. Analisis Kesalahan Pengukuran (Galat)",
            subtitle = "Sumber galat mekanis, paralaks, galat absolut, dan galat relatif",
            category = "Analisis Galat",
            relatedInstrument = InstrumentType.DIGITAL_CALIPER,
            objectives = listOf(
                "Membedakan kesalahan sistematis (zero error, alat aus) dan kesalahan acak/prosedur.",
                "Menghitung galat absolut dan galat relatif dalam persen."
            ),
            sections = listOf(
                LessonSection(
                    heading = "Rumus Galat Pengukuran",
                    body = "Membandingkan hasil pengukuran terhadap nilai acuan (referensi kalibrasi/standar) menentukan tingkat akurasi pengukuran.",
                    formulaHighlight = "Galat = Hasil Ukur − Nilai Acuan   |   Galat Relatif = (|Galat| / Nilai Acuan) × 100%",
                    keyPoints = listOf(
                        "Tekanan Berlebih: Memutar thimble langsung menekan benda kerja hingga terbaca lebih kecil.",
                        "Posisi Miring (Cosine Error): Benda kerja miring menyebabkan hasil lebih besar dari diameter aktual.",
                        "Permukaan Kotor: Serpihan gram/debu menambah pembacaan secara semu.",
                        "Kesalahan Penguncian: Skala bergeser saat alat ditarik dari benda kerja tanpa dikunci."
                    )
                )
            )
        ),
        LessonTopic(
            id = "topic_toleransi",
            title = "8. Toleransi Pemesinan & Kontrol Kualitas",
            subtitle = "Ukuran nominal, batas atas/bawah, penyimpangan, dan keputusan PASS/REJECT",
            category = "Toleransi",
            relatedInstrument = InstrumentType.MANUAL_MICROMETER,
            objectives = listOf(
                "Menghitung batas maksimum dan minimum dari spesifikasi gambar kerja.",
                "Menentukan apakah komponen hasil pemesinan layak diterima (PASS) atau ditolak (REJECT)."
            ),
            sections = listOf(
                LessonSection(
                    heading = "Menghitung Batas Toleransi",
                    body = "Tidak ada proses pemesinan yang menghasilkan ukuran mutlak sempurna. Toleransi adalah selisih penyimpangan yang diizinkan agar komponen tetap dapat dirakit dan berfungsi.",
                    formulaHighlight = "Batas Atas = Nominal + Tol Atas   |   Batas Bawah = Nominal − |Tol Bawah|",
                    keyPoints = listOf(
                        "Hijau (Sesuai): Hasil berada aman di antara Batas Bawah dan Batas Atas.",
                        "Oranye (Perlu Pemeriksaan): Hasil sangat mepet batas toleransi (≤10% lebar toleransi).",
                        "Merah (Tidak Sesuai): Oversize (di atas batas atas) atau Undersize (di bawah batas bawah)."
                    )
                )
            )
        )
    )

    val quizQuestions: List<QuizQuestion> = listOf(
        // SCALE_QUIZ (rendered dynamically by real simulator canvas!)
        QuizQuestion(
            id = "q_scale_1",
            category = EvaluationCategory.SCALE_QUIZ,
            prompt = "Perhatikan skala Jangka Sorong Manual (ketelitian 0,05 mm) di atas. Berapakah hasil pengukurannya?",
            instrumentType = InstrumentType.MANUAL_CALIPER,
            simulatedPositionUm = 25_500,
            caliperResolution = CaliperResolution.RES_0_05_MM,
            options = listOf("25,05 mm", "25,50 mm", "25,55 mm", "26,50 mm"),
            correctOptionIndex = 1,
            explanation = "Skala utama berada di 25 mm dan garis nonius ke-10 berimpit tepat: 25 + (10 × 0,05) = 25,50 mm."
        ),
        QuizQuestion(
            id = "q_scale_2",
            category = EvaluationCategory.SCALE_QUIZ,
            prompt = "Baca hasil pengukuran pada Jangka Sorong Manual dengan ketelitian 0,02 mm berikut:",
            instrumentType = InstrumentType.MANUAL_CALIPER,
            simulatedPositionUm = 18_360,
            caliperResolution = CaliperResolution.RES_0_02_MM,
            options = listOf("18,18 mm", "18,32 mm", "18,36 mm", "18,63 mm"),
            correctOptionIndex = 2,
            explanation = "Skala utama = 18 mm, garis nonius ke-18 berimpit: 18 + (18 × 0,02) = 18,36 mm."
        ),
        QuizQuestion(
            id = "q_scale_3",
            category = EvaluationCategory.SCALE_QUIZ,
            prompt = "Perhatikan posisi sleeve dan thimble Mikrometer Sekrup Manual (0,01 mm) di atas. Hasil ukurnya adalah:",
            instrumentType = InstrumentType.MANUAL_MICROMETER,
            simulatedPositionUm = 10_280,
            options = listOf("10,28 mm", "10,78 mm", "10,23 mm", "11,28 mm"),
            correctOptionIndex = 0,
            explanation = "Skala sleeve terlihat 10,0 mm (garis 0,5 mm belum tampak), skala thimble sejajar 28 → 10,0 + 0,28 = 10,28 mm."
        ),
        QuizQuestion(
            id = "q_scale_4",
            category = EvaluationCategory.SCALE_QUIZ,
            prompt = "Perhatikan garis 0,5 mm di bagian bawah sleeve Mikrometer Manual di atas! Berapakah hasil ukurnya?",
            instrumentType = InstrumentType.MANUAL_MICROMETER,
            simulatedPositionUm = 6_770,
            options = listOf("6,27 mm", "6,57 mm", "6,77 mm", "7,27 mm"),
            correctOptionIndex = 2,
            explanation = "Garis 6,5 mm di bagian bawah sleeve sudah terlihat, ditambah thimble 27 × 0,01 = 0,27 mm → 6,50 + 0,27 = 6,77 mm."
        ),
        QuizQuestion(
            id = "q_scale_5",
            category = EvaluationCategory.SCALE_QUIZ,
            prompt = "Hasil pembacaan Mikrometer Sekrup Digital (ketelitian 0,01 mm) pada layar LCD di atas adalah:",
            instrumentType = InstrumentType.DIGITAL_MICROMETER,
            simulatedPositionUm = 6_280,
            options = listOf("6,25 mm", "6,28 mm", "6,32 mm", "6,38 mm"),
            correctOptionIndex = 1,
            explanation = "Layar LCD menampilkan langsung posisi spindle dengan ketelitian 0,01 mm yaitu 6,28 mm."
        ),
        // PRE_TEST
        QuizQuestion(
            id = "q_pre_1",
            category = EvaluationCategory.PRE_TEST,
            prompt = "Alat ukur manakah yang paling tepat untuk mengukur diameter lubang dalam (bore) blok aluminium?",
            instrumentType = null,
            options = listOf(
                "Mikrometer luar standar 0–25 mm",
                "Jangka sorong menggunakan rahang dalam (inside jaws)",
                "Mikrometer menggunakan anvil dan spindle",
                "Batang kedalaman jangka sorong"
            ),
            correctOptionIndex = 1,
            explanation = "Jangka sorong memiliki rahang dalam (inside jaws) khusus untuk diameter lubang, sedangkan mikrometer luar standar hanya untuk dimensi luar/ketebalan."
        ),
        QuizQuestion(
            id = "q_pre_2",
            category = EvaluationCategory.PRE_TEST,
            prompt = "Sebuah mikrometer manual memiliki pitch ulir 0,5 mm dan 50 skala pada thimble. Berapa ketelitiannya?",
            instrumentType = null,
            options = listOf("0,1 mm", "0,05 mm", "0,02 mm", "0,01 mm"),
            correctOptionIndex = 3,
            explanation = "Ketelitian = Pitch / Jumlah skala thimble = 0,5 mm / 50 = 0,01 mm."
        ),
        QuizQuestion(
            id = "q_pre_3",
            category = EvaluationCategory.PRE_TEST,
            prompt = "Apa fungsi utama Ratchet Stop pada mikrometer sekrup?",
            instrumentType = null,
            options = listOf(
                "Mengunci posisi spindle agar tidak bergerak",
                "Mengonversi satuan mm ke inci",
                "Membatasi gaya penjepitan agar tekanan pengukuran konstan",
                "Membersihkan debu pada permukaan anvil"
            ),
            correctOptionIndex = 2,
            explanation = "Ratchet stop akan slip dan berbunyi klik 2–3 kali saat gaya kontak ideal tercapai sehingga mencegah kesalahan tekanan berlebih."
        ),
        QuizQuestion(
            id = "q_pre_4",
            category = EvaluationCategory.PRE_TEST,
            prompt = "Ukuran nominal poros adalah 12,00 ± 0,05 mm. Jika hasil ukur 12,07 mm, bagaimana status toleransinya?",
            instrumentType = null,
            options = listOf(
                "Sesuai toleransi (PASS)",
                "Tidak sesuai — melebihi batas atas (oversize)",
                "Tidak sesuai — kurang dari batas bawah (undersize)",
                "Tepat di ukuran nominal"
            ),
            correctOptionIndex = 1,
            explanation = "Batas atas = 12,00 + 0,05 = 12,05 mm. Karena 12,07 mm > 12,05 mm, maka komponen oversize (Tidak Sesuai)."
        ),
        // POST_TEST
        QuizQuestion(
            id = "q_post_1",
            category = EvaluationCategory.POST_TEST,
            prompt = "Pada pengecekan nol, jangka sorong memiliki zero error +0,04 mm. Hasil pembacaan poros adalah 20,54 mm. Berapakah ukuran sebenarnya?",
            instrumentType = null,
            options = listOf("20,58 mm", "20,54 mm", "20,50 mm", "20,46 mm"),
            correctOptionIndex = 2,
            explanation = "Ukuran sebenarnya = Hasil baca − Zero Error = 20,54 − (+0,04) = 20,50 mm."
        ),
        QuizQuestion(
            id = "q_post_2",
            category = EvaluationCategory.POST_TEST,
            prompt = "Perhatikan posisi Mikrometer Manual berikut. Tentukan pembacaan yang paling akurat:",
            instrumentType = InstrumentType.MANUAL_MICROMETER,
            simulatedPositionUm = 14_620,
            options = listOf("14,12 mm", "14,52 mm", "14,62 mm", "15,12 mm"),
            correctOptionIndex = 2,
            explanation = "Sleeve menunjukkan 14,5 mm ditambah 12 skala thimble (0,12 mm) = 14,62 mm."
        ),
        QuizQuestion(
            id = "q_post_3",
            category = EvaluationCategory.POST_TEST,
            prompt = "Jika hasil ukur adalah 24,86 mm sedangkan nilai acuan 25,00 mm, berapakah galat absolut dan galat relatifnya?",
            instrumentType = null,
            options = listOf(
                "Galat −0,14 mm; Galat relatif 0,56%",
                "Galat +0,14 mm; Galat relatif 0,56%",
                "Galat −0,14 mm; Galat relatif 1,40%",
                "Galat −0,24 mm; Galat relatif 0,96%"
            ),
            correctOptionIndex = 0,
            explanation = "Galat = 24,86 − 25,00 = −0,14 mm. Galat relatif = (0,14 / 25,00) × 100% = 0,56%."
        ),
        QuizQuestion(
            id = "q_post_4",
            category = EvaluationCategory.POST_TEST,
            prompt = "Perhatikan Jangka Sorong Manual (0,05 mm) di atas. Berapakah nilai pembacaannya?",
            instrumentType = InstrumentType.MANUAL_CALIPER,
            simulatedPositionUm = 31_750,
            caliperResolution = CaliperResolution.RES_0_05_MM,
            options = listOf("31,25 mm", "31,70 mm", "31,75 mm", "32,25 mm"),
            correctOptionIndex = 2,
            explanation = "Skala utama = 31 mm dan garis nonius ke-15 berimpit: 31 + (15 × 0,05) = 31,75 mm."
        )
    )

    val machiningChallenges: List<MachiningChallenge> = listOf(
        MachiningChallenge(
            id = "mc_01",
            title = "Inspeksi Poros Transmisi Ø25",
            drawingCode = "DWG-SMK-101",
            workpieceId = "wp_cyl_shaft",
            zoneId = "z_od",
            recommendedInstrument = InstrumentType.MANUAL_CALIPER,
            nominalUm = 25_500,
            upperTolUm = 50,
            lowerTolUm = 50,
            actualUm = 25_500,
            expectedDecisionPass = true,
            engineeringNote = "Gambar kerja mensyaratkan Ø25,50 ± 0,05 mm (rentang 25,45–25,55 mm). Ukur di simulator lalu putuskan PASS atau REJECT."
        ),
        MachiningChallenge(
            id = "mc_02",
            title = "Quality Control Poros Bertingkat Ø25",
            drawingCode = "DWG-SMK-102",
            workpieceId = "wp_stepped_shaft",
            zoneId = "z_od_large",
            recommendedInstrument = InstrumentType.DIGITAL_CALIPER,
            nominalUm = 25_000,
            upperTolUm = 50,
            lowerTolUm = 50,
            actualUm = 24_860,
            expectedDecisionPass = false,
            engineeringNote = "Spesifikasi Ø25,00 ± 0,05 mm (rentang 24,95–25,05 mm). Periksa apakah poros hasil bubut memenuhi toleransi!"
        ),
        MachiningChallenge(
            id = "mc_03",
            title = "Inspeksi Ketebalan Plat Presisi",
            drawingCode = "DWG-SMK-103",
            workpieceId = "wp_flat_plate",
            zoneId = "z_thick",
            recommendedInstrument = InstrumentType.DIGITAL_MICROMETER,
            nominalUm = 6_300,
            upperTolUm = 50,
            lowerTolUm = 50,
            actualUm = 6_280,
            expectedDecisionPass = true,
            engineeringNote = "Ketebalan plat disyaratkan 6,30 ± 0,05 mm (6,25–6,35 mm). Gunakan Mikrometer Sekrup (0,01 mm) untuk verifikasi."
        ),
        MachiningChallenge(
            id = "mc_04",
            title = "Cek Diameter Lubang Bushing Ø15",
            drawingCode = "DWG-SMK-104",
            workpieceId = "wp_bushing_ring",
            zoneId = "z_id",
            recommendedInstrument = InstrumentType.MANUAL_CALIPER,
            nominalUm = 15_000,
            upperTolUm = 30,
            lowerTolUm = 30,
            actualUm = 15_020,
            expectedDecisionPass = true,
            engineeringNote = "Lubang bushing Ø15,00 ± 0,03 mm. Gunakan rahang dalam jangka sorong, bukan mikrometer luar!"
        )
    )
}
