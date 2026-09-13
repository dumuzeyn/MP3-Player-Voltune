#pragma once
#include "tensor.hpp"
#include <Eigen/Dense>
#include <cmath>

// Same im2col mapping and GEMM as upstream, materialized in 256-row tiles.
template<int kh, int kw, int sh, int sw, int ph, int pw, int dh, int dw, bool transpose>
Eigen::MatrixXf voltune_conv_product(const Eigen::Tensor3dXf& input, const Eigen::MatrixXf& weights) {
    const int channels = input.dimension(0), height = input.dimension(1), width = input.dimension(2);
    const int rowsH = transpose ? (height - 1) * sh + kh + (kh - 1) * (dh - 1)
        : static_cast<int>(std::ceil((height + 2 * ph - dh * (kh - 1) - 1) / float(sh))) + 1;
    const int rowsW = transpose ? (width - 1) * sw + kw + (kw - 1) * (dw - 1)
        : static_cast<int>(std::ceil((width + 2 * pw - dw * (kw - 1) - 1) / float(sw))) + 1;
    const int rows = rowsH * rowsW;
    Eigen::MatrixXf result(rows, weights.rows());
    #pragma omp parallel for num_threads(2) schedule(static)
    for (int offset = 0; offset < rows; offset += 256) {
        const int count = std::min(256, rows - offset);
        Eigen::MatrixXf tile = Eigen::MatrixXf::Zero(count, channels * kh * kw);
        for (int c = 0; c < channels; ++c) for (int h = 0; h < kh; ++h) for (int w = 0; w < kw; ++w) {
            const int column = c * kh * kw + h * kw + w;
            for (int i = 0; i < count; ++i) {
                const int y = (offset + i) / rowsW, x = (offset + i) % rowsW;
                int sourceY, sourceX;
                if constexpr (transpose) {
                    const int expandedY = y - h * dh + ph, expandedX = x - w * dw + pw;
                    if (expandedY < 0 || expandedX < 0 || expandedY % sh || expandedX % sw) continue;
                    sourceY = expandedY / sh;
                    sourceX = expandedX / sw;
                } else {
                    sourceY = y * sh + h * dh - ph;
                    sourceX = x * sw + w * dw - pw;
                }
                if (sourceY >= 0 && sourceY < height && sourceX >= 0 && sourceX < width)
                    tile(i, column) = input(c, sourceY, sourceX);
            }
        }
        result.middleRows(offset, count).noalias() = tile * weights.transpose();
    }
    return result;
}
