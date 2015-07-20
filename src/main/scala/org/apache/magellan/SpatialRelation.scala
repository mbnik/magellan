/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.magellan

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.expressions.GenericMutableRow
import org.apache.spark.sql.sources.{Filter, BaseRelation, PrunedFilteredScan}
import org.apache.spark.sql.types._

private[magellan] trait SpatialRelation extends BaseRelation with PrunedFilteredScan {

  @transient val sc = sqlContext.sparkContext

  override val schema = {
    StructType(List(StructField("point", new PointUDT(), true),
        StructField("polyline", new PolyLineUDT(), true),
        StructField("polygon", new PolygonUDT(), true),
        StructField("metadata", MapType(StringType, StringType, true), true),
        StructField("valid", BooleanType, true)
      ))
  }

  def _buildScan(): RDD[(Shape, Option[Map[String, String]])]

  override def buildScan(requiredColumns: Array[String], filters: Array[Filter]) = {
    val indices = requiredColumns.map(attr => schema.fieldIndex(attr))
    val numFields = indices.size
    _buildScan().mapPartitions { iter =>
      val row = new GenericMutableRow(numFields)
      iter.flatMap { case (shape: Shape, meta: Option[Map[String, String]]) =>
        (0 until numFields).foreach(i => row.setNullAt(i))
        indices.zipWithIndex.foreach { case (index, i) =>
          val v = index match {
            case 0 => if (shape.isInstanceOf[Point]) Some(shape) else None
            case 1 => if (shape.isInstanceOf[PolyLine]) Some(shape) else None
            case 2 => if (shape.isInstanceOf[Polygon]) Some(shape) else None
            case 3 => Some(meta.fold(Map[String, String]())(identity))
            case 4 => Some(shape.isValid())
          }
          v.foreach(x => row(i) = x)
          }
        Some(row)
      }
    }
  }
}
