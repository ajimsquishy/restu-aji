package com.example.data.catalog

import com.example.domain.model.DifficultyLevel
import com.example.domain.model.MeasurementKind
import com.example.domain.model.MeasurementZone
import com.example.domain.model.Workpiece
import com.example.domain.model.WorkpieceShapeType

/**
 * Built-in offline catalog of 10 standard CNC/conventional machining workpieces.
 * Every dimension is stored in integer micrometers (µm) and directly coupled to the simulation engine.
 */
object WorkpieceCatalog {
    val defaultWorkpieces: List<Workpiece> = listOf(
        Workpiece(
            id = "wp_cyl_shaft",
            name = "Poros Silinder Ø25",
            shapeType = WorkpieceShapeType.CYLINDER_SHAFT,
            material = "Baja S45C",
            description = "Poros hasil pembubutan rata untuk latihan pengukuran diameter luar dan panjang total.",
            difficulty = DifficultyLevel.BASIC,
            zones = listOf(
                MeasurementZone("z_od", "Diameter Luar Poros", MeasurementKind.OUTSIDE_DIAMETER, 25_500, 25_500, 50, 50),
                MeasurementZone("z_len", "Panjang Poros", MeasurementKind.OUTSIDE_DIAMETER, 60_200, 60_000, 250, 250)
            )
        ),
        Workpiece(
            id = "wp_stepped_shaft",
            name = "Poros Bertingkat",
            shapeType = WorkpieceShapeType.STEPPED_SHAFT,
            material = "Baja ST37",
            description = "Poros dua tingkat untuk latihan pengukuran diameter luar presisi serta panjang bahu (step).",
            difficulty = DifficultyLevel.INTERMEDIATE,
            zones = listOf(
                MeasurementZone("z_od_large", "Diameter Tingkat Besar", MeasurementKind.OUTSIDE_DIAMETER, 24_860, 25_000, 50, 50),
                MeasurementZone("z_od_small", "Diameter Tingkat Kecil", MeasurementKind.OUTSIDE_DIAMETER, 12_350, 12_350, 25, 25),
                MeasurementZone("z_step", "Panjang Bahu (Step)", MeasurementKind.STEP, 18_400, 18_500, 120, 120)
            )
        ),
        Workpiece(
            id = "wp_grooved_shaft",
            name = "Poros Beralur (Snap Ring)",
            shapeType = WorkpieceShapeType.GROOVED_SHAFT,
            material = "Baja Paduan SCM440",
            description = "Poros dengan alur melingkar untuk pengujian diameter poros, dasar alur, dan kedalaman alur.",
            difficulty = DifficultyLevel.INTERMEDIATE,
            zones = listOf(
                MeasurementZone("z_od", "Diameter Luar Poros", MeasurementKind.OUTSIDE_DIAMETER, 20_020, 20_000, 40, 40),
                MeasurementZone("z_groove_od", "Diameter Dasar Alur", MeasurementKind.OUTSIDE_DIAMETER, 14_480, 14_500, 50, 50),
                MeasurementZone("z_groove_depth", "Kedalaman Alur", MeasurementKind.DEPTH, 2_760, 2_750, 50, 50)
            )
        ),
        Workpiece(
            id = "wp_bushing_ring",
            name = "Bushing Perunggu",
            shapeType = WorkpieceShapeType.BUSHING_RING,
            material = "Perunggu SAE 660",
            description = "Bushing bantalan presisi untuk latihan pengukuran diameter luar, diameter lubang dalam, dan panjang bushing.",
            difficulty = DifficultyLevel.BASIC,
            zones = listOf(
                MeasurementZone("z_od", "Diameter Luar Bushing", MeasurementKind.OUTSIDE_DIAMETER, 22_400, 22_400, 40, 40),
                MeasurementZone("z_id", "Diameter Lubang Dalam", MeasurementKind.INSIDE_DIAMETER, 15_020, 15_000, 30, 30),
                MeasurementZone("z_thick", "Tebal / Panjang Cincin", MeasurementKind.THICKNESS, 10_280, 10_000, 300, 300)
            )
        ),
        Workpiece(
            id = "wp_hex_bolt",
            name = "Baut Kepala Heksagonal M10",
            shapeType = WorkpieceShapeType.HEX_BOLT,
            material = "Baja Karbon Grade 8.8",
            description = "Baut standar metrik untuk pengukuran diameter mayor ulir, lebar kunci kepala, dan panjang batang.",
            difficulty = DifficultyLevel.BASIC,
            zones = listOf(
                MeasurementZone("z_major", "Diameter Luar Ulir", MeasurementKind.OUTSIDE_DIAMETER, 9_920, 10_000, 0, 120),
                MeasurementZone("z_hex", "Lebar Kepala Kunci", MeasurementKind.THICKNESS, 16_950, 17_000, 80, 80),
                MeasurementZone("z_len", "Panjang Batang Baut", MeasurementKind.STEP, 45_150, 45_000, 200, 200)
            )
        ),
        Workpiece(
            id = "wp_hex_nut",
            name = "Mur Heksagonal M10",
            shapeType = WorkpieceShapeType.HEX_NUT,
            material = "Baja Galvanis",
            description = "Mur metrik untuk pengukuran lebar antar sisi datar, lubang inti, dan ketebalan mur.",
            difficulty = DifficultyLevel.BASIC,
            zones = listOf(
                MeasurementZone("z_across_flats", "Lebar Sisi Datar", MeasurementKind.THICKNESS, 16_960, 17_000, 60, 60),
                MeasurementZone("z_bore", "Diameter Lubang Inti", MeasurementKind.INSIDE_DIAMETER, 8_520, 8_500, 60, 60),
                MeasurementZone("z_height", "Ketebalan Mur", MeasurementKind.THICKNESS, 8_020, 8_000, 50, 50)
            )
        ),
        Workpiece(
            id = "wp_flat_plate",
            name = "Plat Baja Presisi",
            shapeType = WorkpieceShapeType.FLAT_PLATE,
            material = "Baja Pelat SPHC",
            description = "Plat hasil frais/gerinda datar untuk pengukuran ketebalan presisi tinggi dengan mikrometer.",
            difficulty = DifficultyLevel.BASIC,
            zones = listOf(
                MeasurementZone("z_thick", "Ketebalan Plat", MeasurementKind.THICKNESS, 6_280, 6_300, 50, 50),
                MeasurementZone("z_width", "Lebar Plat", MeasurementKind.OUTSIDE_DIAMETER, 38_500, 38_500, 100, 100)
            )
        ),
        Workpiece(
            id = "wp_holed_block",
            name = "Blok Berlubang Buta (Blind Hole)",
            shapeType = WorkpieceShapeType.HOLED_BLOCK,
            material = "Aluminium 6061-T6",
            description = "Blok hasil pengeboran untuk melatih rahang dalam dan batang kedalaman jangka sorong.",
            difficulty = DifficultyLevel.INTERMEDIATE,
            zones = listOf(
                MeasurementZone("z_thick", "Tebal Blok", MeasurementKind.THICKNESS, 18_040, 18_000, 50, 50),
                MeasurementZone("z_hole_id", "Diameter Lubang", MeasurementKind.INSIDE_DIAMETER, 14_200, 14_200, 50, 50),
                MeasurementZone("z_hole_depth", "Kedalaman Lubang", MeasurementKind.DEPTH, 10_000, 10_000, 50, 50)
            )
        ),
        Workpiece(
            id = "wp_pipe",
            name = "Pipa Silinder Seamless",
            shapeType = WorkpieceShapeType.PIPE,
            material = "Stainless Steel 304",
            description = "Pipa industri untuk membandingkan diameter luar, diameter dalam, dan ketebalan dinding.",
            difficulty = DifficultyLevel.INTERMEDIATE,
            zones = listOf(
                MeasurementZone("z_od", "Diameter Luar Pipa", MeasurementKind.OUTSIDE_DIAMETER, 21_320, 21_300, 80, 80),
                MeasurementZone("z_id", "Diameter Dalam Pipa", MeasurementKind.INSIDE_DIAMETER, 16_100, 16_100, 80, 80),
                MeasurementZone("z_wall", "Ketebalan Dinding", MeasurementKind.THICKNESS, 2_610, 2_600, 50, 50)
            )
        ),
        Workpiece(
            id = "wp_stepped_bore",
            name = "Blok Berlubang Bertingkat (Counterbore)",
            shapeType = WorkpieceShapeType.STEPPED_BORE_BLOCK,
            material = "Baja Tool SKD11",
            description = "Benda kerja tingkat lanjut dengan lubang counterbore bertingkat untuk evaluasi lengkap.",
            difficulty = DifficultyLevel.ADVANCED,
            zones = listOf(
                MeasurementZone("z_bore_top", "Diameter Lubang Atas", MeasurementKind.INSIDE_DIAMETER, 20_050, 20_000, 60, 60),
                MeasurementZone("z_bore_bottom", "Diameter Lubang Bawah", MeasurementKind.INSIDE_DIAMETER, 12_020, 12_000, 40, 40),
                MeasurementZone("z_depth_step1", "Kedalaman Tingkat 1", MeasurementKind.DEPTH, 8_500, 8_500, 50, 50),
                MeasurementZone("z_depth_total", "Kedalaman Total Lubang", MeasurementKind.DEPTH, 19_200, 19_200, 80, 80),
                MeasurementZone("z_block_height", "Tinggi Blok", MeasurementKind.THICKNESS, 24_000, 24_000, 60, 60)
            )
        ),
        Workpiece(
            id = "wp_ring",
            name = "Cincin Spacer Presisi",
            shapeType = WorkpieceShapeType.RING,
            material = "Baja Karbon S50C",
            description = "Cincin spacer tipis hasil bubut dan gerinda datar untuk pengukuran diameter luar, lubang dalam, dan ketebalan cincin.",
            difficulty = DifficultyLevel.BASIC,
            zones = listOf(
                MeasurementZone("z_ring_od", "Diameter Luar Cincin", MeasurementKind.OUTSIDE_DIAMETER, 22_000, 22_000, 40, 40),
                MeasurementZone("z_ring_id", "Diameter Lubang Cincin", MeasurementKind.INSIDE_DIAMETER, 14_020, 14_000, 30, 30),
                MeasurementZone("z_ring_thick", "Ketebalan Cincin", MeasurementKind.THICKNESS, 4_500, 4_500, 30, 30)
            )
        )
    )
}
