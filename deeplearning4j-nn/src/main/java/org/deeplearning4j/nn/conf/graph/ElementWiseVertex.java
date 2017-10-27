/*-
 *
 *  * Copyright 2016 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.conf.graph;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.conf.memory.LayerMemoryReport;
import org.deeplearning4j.nn.conf.memory.MemoryReport;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Collection;

/**
 * An ElementWiseVertex is used to combine the activations of two or more layer in an element-wise manner<br>
 * For example, the activations may be combined by addition, subtraction or multiplication or by selecting the maximum.
 * Addition, Average, Max and Product may use an arbitrary number of input arrays. Note that in the case of subtraction, only two inputs may be used.
 *
 * @author Alex Black
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ElementWiseVertex extends BaseGraphVertex {

    public ElementWiseVertex(@JsonProperty("op") Op op) {
        this.op = op;
    }

    public enum Op {
        Add, Subtract, Product, Average, Max
    }

    protected Op op;

    @Override
    public ElementWiseVertex clone() {
        return new ElementWiseVertex(op);
    }

    @Override
    public int minInputs() {
        return 2;
    }

    @Override
    public int maxInputs() {
        switch (op) {
            case Add:
            case Average:
            case Product:
            case Max:
                //No upper bound
                return Integer.MAX_VALUE;
            case Subtract:
                return 2;
            default:
                throw new UnsupportedOperationException("Unknown op: " + op);
        }
    }

    @Override
    public Layer instantiate(Collection<IterationListener> iterationListeners,
                             String name, int idx, int numInputs, INDArray layerParamsView,
                             boolean initializeParams) {
        org.deeplearning4j.nn.graph.vertex.impl.ElementWiseVertex.Op op;
        switch (this.op) {
            case Add:
                op = org.deeplearning4j.nn.graph.vertex.impl.ElementWiseVertex.Op.Add;
                break;
            case Average:
                op = org.deeplearning4j.nn.graph.vertex.impl.ElementWiseVertex.Op.Average;
                break;
            case Subtract:
                op = org.deeplearning4j.nn.graph.vertex.impl.ElementWiseVertex.Op.Subtract;
                break;
            case Product:
                op = org.deeplearning4j.nn.graph.vertex.impl.ElementWiseVertex.Op.Product;
                break;
            case Max:
                op = org.deeplearning4j.nn.graph.vertex.impl.ElementWiseVertex.Op.Max;
                break;
            default:
                throw new RuntimeException();
        }
        return new org.deeplearning4j.nn.graph.vertex.impl.ElementWiseVertex(name, idx, numInputs, op);
    }

    @Override
    public InputType[] getOutputType(int layerIndex, InputType... vertexInputs) throws InvalidInputTypeException {
        if (vertexInputs.length == 1)
            return vertexInputs;
        InputType first = vertexInputs[0];
        if (first.getType() != InputType.Type.CNN) {
            //FF, RNN or flat CNN data inputs
            for (int i = 1; i < vertexInputs.length; i++) {
                if (vertexInputs[i].getType() != first.getType()) {
                    throw new InvalidInputTypeException(
                            "Invalid input: ElementWise vertex cannot process activations of different types:"
                                    + " first type = " + first.getType() + ", input type " + (i + 1)
                                    + " = " + vertexInputs[i].getType());
                }
            }
        } else {
            //CNN inputs... also check that the depth, width and heights match:
            InputType.InputTypeConvolutional firstConv = (InputType.InputTypeConvolutional) first;
            int fd = firstConv.getDepth();
            int fw = firstConv.getWidth();
            int fh = firstConv.getHeight();

            for (int i = 1; i < vertexInputs.length; i++) {
                if (vertexInputs[i].getType() != InputType.Type.CNN) {
                    throw new InvalidInputTypeException(
                            "Invalid input: ElementWise vertex cannot process activations of different types:"
                                    + " first type = " + InputType.Type.CNN + ", input type " + (i + 1)
                                    + " = " + vertexInputs[i].getType());
                }

                InputType.InputTypeConvolutional otherConv = (InputType.InputTypeConvolutional) vertexInputs[i];

                int od = otherConv.getDepth();
                int ow = otherConv.getWidth();
                int oh = otherConv.getHeight();

                if (fd != od || fw != ow || fh != oh) {
                    throw new InvalidInputTypeException(
                            "Invalid input: ElementWise vertex cannot process CNN activations of different sizes:"
                                    + "first [depth,width,height] = [" + fd + "," + fw + "," + fh
                                    + "], input " + i + " = [" + od + "," + ow + "," + oh + "]");
                }
            }
        }
        return new InputType[]{first}; //Same output shape/size as
    }

    @Override
    public LayerMemoryReport getMemoryReport(InputType... inputTypes) {
        //No working memory in addition to output activations
        return new LayerMemoryReport.Builder(null, ElementWiseVertex.class, inputTypes[0], inputTypes[0])
                .standardMemory(0, 0).workingMemory(0, 0, 0, 0).cacheMemory(0, 0).build();
    }
}