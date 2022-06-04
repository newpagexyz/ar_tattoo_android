#include <jni.h>
#include <android/log.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#define TAG "NativeLib"

inline double distance(cv::Point a, cv::Point b) {
    return sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
}


inline cv::Vec2f findOrthogonal(cv::Vec2f vector) {
    float a = vector[0];
    float b = vector[1];
    float c = b * b / (a * a + b * b);
    float d = 1 - c * c;
    return cv::Vec2f(std::sqrt(c) * 300, std::sqrt(d) * 300);
}

std::vector<cv::Point2f> points3d = {
        cv::Point2f(0.0, 720.0),
        cv::Point2f(1280.0, 720.0),
        cv::Point2f(1280.0, 0.0),
        cv::Point2f(0.0, 0.0)
};

std::vector<cv::Point2f> intersections = {
        cv::Point2f(0.0, 0.0),
        cv::Point2f(0.0, 0.0),
        cv::Point2f(0.0, 0.0),
        cv::Point2f(0.0, 0.0)
};

bool isIntersectionsNotEmpty(std::vector<cv::Point2f> &intersections) {
    for (int i = 0; i < intersections.size(); i++)
    {
        if (norm(intersections[i]) >= 0.0) return true;
    }
    return false;
}

void findIntersections(cv::Mat &mat, std::vector<cv::Point2f> &oldIntersections) {
    const cv::Scalar lowerBound = cv::Scalar(0, 48, 80);
    const cv::Scalar upperBound = cv::Scalar(20, 255, 255);

    cv::Mat thresh;

    cv::cvtColor(mat, thresh, cv::COLOR_RGB2HSV);
    cv::inRange(thresh, lowerBound, upperBound, thresh);
    cv::threshold(thresh, thresh, 170, 255, cv::THRESH_BINARY);


    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;

    cv::findContours(thresh, contours, hierarchy, cv::RETR_TREE, cv::CHAIN_APPROX_TC89_KCOS);

    int max_index = 0;
    double max_area = 0;

    for (int i = 0; i < contours.size(); i++) {
        double area = cv::contourArea(contours[i], false);
        if (area > max_area) {
            max_area = area;
            max_index = i;
        }
    }

    if (contours.empty()) return;

    std::vector<std::vector<cv::Point>> hull(1);
    cv::convexHull(contours[max_index], hull[0]);

    cv::Moments mu;
    mu = cv::moments(contours[max_index], false);


    int cx = mu.m10 / mu.m00;
    int cy = mu.m01 / mu.m00;

    cv::Point center_of_mass(cx, cy);

    std::vector<cv::Point> nodes(hull[0].size());
    std::vector<cv::Point> bottomTriangle(2);

    for (int i = 0; i < hull[0].size(); i++) {
        nodes[i] = hull[0][i];
    }

    double max_angle = 0;


    cv::Point nodeA = nodes[0];
    for (int i = nodes.size() - 1; i >= 0; i--) {
        cv::Point nodeB = nodes[i];
        double c = distance(nodeA, nodeB);
        double a = distance(nodeB, center_of_mass);
        double b = distance(center_of_mass, nodeA);

        double gamma = std::acos((a * a + b * b - c * c) / (2 * a * b));
        if ((gamma >= max_angle) && (gamma < 3.14 / 2)) {
            max_angle = gamma;
            bottomTriangle[0] = cv::Point(nodeA.x, nodeA.y);
            bottomTriangle[1] = cv::Point(nodeB.x, nodeB.y);
        }
        nodeA = nodeB;
    }

    cv::Vec2f vectorA = cv::Vec2f(center_of_mass.x - bottomTriangle[0].x,
                                  center_of_mass.y - bottomTriangle[0].y);
    cv::Vec2f vectorB = cv::Vec2f(center_of_mass.x - bottomTriangle[1].x,
                                  center_of_mass.y - bottomTriangle[1].y);

    cv::Vec2f mainVector = vectorA + vectorB;
    cv::Vec2f orthogonalVector = findOrthogonal(mainVector);


    cv::Mat blank = cv::Mat::zeros(cv::Size(mat.cols, mat.rows), CV_8UC1);
    cv::Mat blank1 = cv::Mat::zeros(cv::Size(mat.cols, mat.rows), CV_8UC1);
    cv::Mat blank2 = cv::Mat::zeros(cv::Size(mat.cols, mat.rows), CV_8UC1);


    cv::drawContours(blank1, contours, max_index, 1);
    cv::line(blank2, center_of_mass, cv::Point((int) (center_of_mass.x + orthogonalVector[0]),
                                               (int) (center_of_mass.y + orthogonalVector[1])), 1,
             1);
    cv::line(blank2, center_of_mass, cv::Point((int) (center_of_mass.x - orthogonalVector[0]),
                                               (int) (center_of_mass.y - orthogonalVector[1])), 1,
             1);

    cv::bitwise_and(blank1, blank2, blank);
    std::vector<cv::Point2i> intersections;
    cv::findNonZero(blank, intersections);


    blank = cv::Mat::zeros(cv::Size(mat.cols, mat.rows), CV_8UC1);
    blank2 = cv::Mat::zeros(cv::Size(mat.cols, mat.rows), CV_8UC1);

    cv::line(blank2, center_of_mass,
             cv::Point((int) (center_of_mass.x - mainVector[0] / 2 + orthogonalVector[0]),
                       (int) (center_of_mass.y - mainVector[1] / 2 + orthogonalVector[1])), 1, 1);
    cv::line(blank2, center_of_mass,
             cv::Point((int) (center_of_mass.x - mainVector[0] / 2 - orthogonalVector[0]),
                       (int) (center_of_mass.y - mainVector[1] / 2 - orthogonalVector[1])), 1, 1);

    cv::bitwise_and(blank1, blank2, blank);
    std::vector<cv::Point2i> intersections2;
    cv::findNonZero(blank, intersections2);

    if ((intersections.size() >= 2) && (intersections2.size() >= 2))
    {
        oldIntersections = {
                cv::Point2f(intersections[0].x, intersections[0].y),
                cv::Point2f(intersections[1].x, intersections[1].y),
                cv::Point2f(intersections2[1].x, intersections2[1].y),
                cv::Point2f(intersections2[0].x, intersections2[0].y)
        };
    }
}

extern "C" {
void JNICALL
Java_com_example_nativeopencvandroidtemplate_MainActivity_adaptiveThresholdFromJNI(JNIEnv *env,
                                                                                   jobject instance,
                                                                                   jlong matAddr,
                                                                                   jlong tattooAddr
) {


    cv::Mat &mat = *(cv::Mat *) matAddr;      //Mat передается в RGB!
    cv::Mat tattoo = *(cv::Mat *) tattooAddr;

    cv::resize(tattoo, tattoo, cv::Size(), 0.5, 0.5);

    findIntersections(mat, intersections);

    if (isIntersectionsNotEmpty(intersections)) {

        cv::Mat M = cv::getPerspectiveTransform(points3d, intersections);
        cv::Mat dframe;
        cv::warpPerspective(tattoo, dframe, M, cv::Size(mat.cols, mat.rows));

        cv::Mat mask = cv::Mat::zeros(mat.rows, mat.cols, CV_8UC1);
        cv::Mat blank = cv::Mat::zeros(mat.rows, mat.cols, CV_8UC4);

        std::vector<cv::Point2i> points2dint = {
                cv::Point2i(intersections[0].x, intersections[0].y),
                cv::Point2i(intersections[1].x, intersections[1].y),
                cv::Point2i(intersections[2].x, intersections[2].y),
                cv::Point2i(intersections[3].x, intersections[3].y),
        };

        cv::fillConvexPoly(mask, points2dint, 255);
        cv::Mat mask_inv;
        cv::bitwise_not(mask, mask_inv);

        cv::bitwise_and(dframe, blank, dframe, mask_inv);
        cv::bitwise_and(mat, blank, mat, mask);
        cv::add(mat, dframe, mat);

    }

}
}