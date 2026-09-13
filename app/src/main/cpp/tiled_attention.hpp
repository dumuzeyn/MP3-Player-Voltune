#pragma once
#include <Eigen/Dense>
#include <cmath>

// Exact row-wise attention with bounded score storage. Each query still sees all keys.
inline Eigen::MatrixXf voltune_attention(const Eigen::MatrixXf& q, const Eigen::MatrixXf& k,
                                       const Eigen::MatrixXf& v, int heads) {
    const int width = static_cast<int>(q.cols()) / heads;
    Eigen::MatrixXf result(q.rows(), q.cols());
    #pragma omp parallel for num_threads(2) schedule(static)
    for (int head = 0; head < heads; ++head) {
        const auto keys = k.middleCols(head * width, width);
        const auto values = v.middleCols(head * width, width);
        for (int row = 0; row < q.rows(); row += 128) {
            const int count = std::min(128, static_cast<int>(q.rows()) - row);
            Eigen::MatrixXf scores = q.block(row, head * width, count, width) * keys.transpose();
            scores /= std::sqrt(static_cast<float>(width));
            for (int i = 0; i < count; ++i) {
                const float maximum = scores.row(i).maxCoeff();
                scores.row(i) = (scores.row(i).array() - maximum).exp();
                scores.row(i) /= scores.row(i).sum();
            }
            result.block(row, head * width, count, width).noalias() = scores * values;
        }
    }
    return result;
}
